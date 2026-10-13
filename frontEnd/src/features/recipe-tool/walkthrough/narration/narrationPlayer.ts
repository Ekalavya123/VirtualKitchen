/**
 * Narration-driven playback for the step slideshow. Framework-free (like saveCoordinator.ts), with
 * the audio element, the narration API and the clock injected, so the playback rules are
 * unit-testable (frontEnd/tests/narrationPlayer.test.ts).
 *
 * In play mode each step works like this: show the step, get its narration (reuse it if READY,
 * otherwise ask the backend to generate it and poll), play it, wait a short dwell, then move on.
 * A step that has no narration (narration off, no text, generation failed, audio error) does not
 * stall the slideshow: it stays on screen for a reading-time dwell instead.
 *
 * Cost rules the player relies on and keeps:
 * - Reading narration state never generates. `ensure` is only called for the step about to play,
 *   plus one prefetch of the next step while the current one plays.
 * - Speed, mute and pause act on the existing audio only and never touch the backend.
 */

import type { StepNarration } from '../../../../types/narration'

/** The parts of an HTMLAudioElement the player uses (see narrationAudio.ts for the real one). */
export interface NarrationAudio {
  load(url: string): void
  play(): Promise<void>
  pause(): void
  /** Rewinds to the start without changing play/pause state. */
  rewind(): void
  /** Pauses and drops the current source. */
  stop(): void
  setMuted(muted: boolean): void
  setPlaybackRate(rate: number): void
  /** Registers the handlers once; the player ignores events for audio it has since replaced. */
  setHandlers(handlers: { onEnded: () => void; onError: () => void }): void
  /** Moves the playback position of the loaded audio, keeping play/pause state. */
  seek(seconds: number): void
  /** The playback position; `duration` is NaN (or Infinity) while unknown. */
  getProgress(): NarrationProgress
  /**
   * Notifies whenever the position may have changed (time updates, seeks, source changes). Kept
   * apart from the player state on purpose: it fires many times a second while audio plays.
   */
  subscribeProgress(listener: () => void): () => void
}

export type NarrationProgress = { currentTime: number; duration: number }

/** Narration state from the backend for one process's steps. */
export interface NarrationSource {
  list(): Promise<StepNarration[]>
  ensure(stepId: string, force?: boolean): Promise<StepNarration>
}

export type PlayerStep = { id: string; text?: string }

/**
 * What the current step's narration is doing:
 * - idle: nothing requested (manual browsing, or before Play)
 * - loading: asking the backend / buffering the audio
 * - generating: the backend is synthesizing; the player polls
 * - playing / paused / ended: the audio itself
 * - blocked: the browser refused autoplay; a click on Play resumes
 * - unavailable: this step has no narration (no text, narration off, or nowhere to fetch it from)
 * - failed: generation or playback failed; `failureReason` says why
 */
export type NarrationPhase =
  | 'idle'
  | 'loading'
  | 'generating'
  | 'playing'
  | 'paused'
  | 'ended'
  | 'blocked'
  | 'unavailable'
  | 'failed'

export type NarrationPlayerState = {
  index: number
  /** Play mode: steps advance on their own. */
  autoplay: boolean
  /** True after the last step finished in play mode. */
  finished: boolean
  phase: NarrationPhase
  failureReason: string | null
  narrationEnabled: boolean
  muted: boolean
  playbackRate: number
  /** Latest known narration per step id (from list/ensure/poll). */
  narrations: Record<string, StepNarration>
}

type TimerHandle = ReturnType<typeof setTimeout>

export type NarrationPlayerOptions = {
  steps: PlayerStep[]
  /** Null when narration cannot be fetched at all (e.g. the process is not saved yet). */
  source: NarrationSource | null
  audio: NarrationAudio
  initial?: Partial<Pick<NarrationPlayerState, 'narrationEnabled' | 'muted' | 'playbackRate'>>
  /** Pause between a narration ending and the next step. */
  dwellMs?: number
  /** Minimum time a step without narration stays on screen in play mode. */
  silentDwellMinMs?: number
  /** Reading time per word for a step without narration. */
  silentMsPerWord?: number
  pollMs?: number
  /** Give up waiting for generation after this long and fall back to a silent step. */
  generationTimeoutMs?: number
  now?: () => number
  setTimer?: (callback: () => void, ms: number) => TimerHandle
  clearTimer?: (handle: TimerHandle) => void
}

export const PLAYBACK_RATES = [0.9, 1, 1.1, 1.2, 1.5] as const

export class NarrationPlayer {
  private state: NarrationPlayerState
  private listeners = new Set<() => void>()
  private steps: PlayerStep[]
  private readonly source: NarrationSource | null
  private readonly audio: NarrationAudio
  private readonly dwellMs: number
  private readonly silentDwellMinMs: number
  private readonly silentMsPerWord: number
  private readonly pollMs: number
  private readonly generationTimeoutMs: number
  private readonly now: () => number
  private readonly setTimer: (callback: () => void, ms: number) => TimerHandle
  private readonly clearTimer: (handle: TimerHandle) => void

  /** Bumped whenever the current step's work is abandoned; async results carrying an older value are ignored. */
  private generation = 0
  private timer: TimerHandle | null = null
  private generatingSince: number | null = null
  private prefetched = new Set<string>()
  private disposed = false

  constructor(options: NarrationPlayerOptions) {
    this.steps = options.steps
    this.source = options.source
    this.audio = options.audio
    this.dwellMs = options.dwellMs ?? 600
    this.silentDwellMinMs = options.silentDwellMinMs ?? 1800
    this.silentMsPerWord = options.silentMsPerWord ?? 350
    this.pollMs = options.pollMs ?? 1500
    this.generationTimeoutMs = options.generationTimeoutMs ?? 90_000
    this.now = options.now ?? Date.now
    this.setTimer = options.setTimer ?? ((callback, ms) => setTimeout(callback, ms))
    this.clearTimer = options.clearTimer ?? ((handle) => clearTimeout(handle))
    this.state = {
      index: 0,
      autoplay: false,
      finished: false,
      phase: 'idle',
      failureReason: null,
      narrationEnabled: options.initial?.narrationEnabled ?? true,
      muted: options.initial?.muted ?? false,
      playbackRate: options.initial?.playbackRate ?? 1,
      narrations: {},
    }
    this.audio.setMuted(this.state.muted)
    this.audio.setPlaybackRate(this.state.playbackRate)
    this.audio.setHandlers({ onEnded: () => this.handleEnded(), onError: () => this.handleAudioError() })
  }

  getState = () => this.state

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  /** Loads what narration already exists. Never generates. Failures are ignored (steps then load lazily). */
  async init(): Promise<void> {
    if (!this.source) return
    try {
      this.mergeNarrations(await this.source.list())
    } catch {
      // Lazy per-step loading still works without the initial snapshot.
    }
  }

  /** Replaces the step list (e.g. after a re-render), keeping the position if the current step still exists. */
  setSteps(steps: PlayerStep[]) {
    const currentId = this.steps[this.state.index]?.id
    this.steps = steps
    const kept = steps.findIndex((step) => step.id === currentId)
    if (kept >= 0) {
      if (kept !== this.state.index) this.update({ index: kept })
      return
    }
    this.abandonStep()
    this.update({ index: Math.min(this.state.index, Math.max(0, steps.length - 1)), phase: 'idle', failureReason: null })
  }

  // --- transport -----------------------------------------------------------------------------

  /** Enters play mode: resumes paused audio, or starts the current step (from the top after finishing). */
  play() {
    if (this.steps.length === 0) return
    if (this.state.finished) {
      this.abandonStep()
      this.update({ index: 0, finished: false, autoplay: true })
      this.startStep()
      return
    }
    this.update({ autoplay: true })
    if (this.state.phase === 'paused' || this.state.phase === 'blocked') {
      this.resumeAudio()
    } else if (this.state.phase === 'ended') {
      this.advance()
    } else if (this.state.phase === 'playing') {
      // Already playing: nothing to do.
    } else {
      this.startStep()
    }
  }

  /** Leaves play mode. Playing audio keeps its position; pending generation polling stops. */
  pause() {
    this.cancelTimer()
    if (this.state.phase === 'playing') {
      this.audio.pause()
      this.update({ autoplay: false, phase: 'paused' })
      return
    }
    if (this.state.phase === 'loading' || this.state.phase === 'generating') {
      // Drop the in-flight work; resuming re-runs the step, which never generates twice.
      this.generation += 1
      this.update({ autoplay: false, phase: 'idle' })
      return
    }
    this.update({ autoplay: false })
  }

  /** Play/Pause button. When the browser blocked autoplay, the click is the gesture that unblocks it. */
  toggle() {
    if (this.state.autoplay && this.state.phase !== 'blocked') this.pause()
    else this.play()
  }

  next() {
    if (this.state.index < this.steps.length - 1) this.goTo(this.state.index + 1)
  }

  previous() {
    if (this.state.index > 0) this.goTo(this.state.index - 1)
  }

  /** Jumps to a step. In play mode it starts narrating; otherwise it only shows the step. */
  goTo(index: number) {
    if (index < 0 || index >= this.steps.length) return
    this.abandonStep()
    this.update({ index, finished: false, phase: 'idle', failureReason: null })
    if (this.state.autoplay) this.startStep()
  }

  /** Plays the current step's narration from the start, whether or not in play mode. */
  replay() {
    if (this.steps.length === 0) return
    const narration = this.currentNarration()
    if (narration?.status === 'READY' && narration.audioUrl && this.isAudioLoadedFor(narration.audioUrl)) {
      this.cancelTimer()
      this.audio.rewind()
      this.resumeAudio()
      return
    }
    this.startStep()
  }

  /** Asks for the current step's narration again after a failure (the backend still applies its retry back-off). */
  retry() {
    this.startStep()
  }

  setMuted(muted: boolean) {
    this.audio.setMuted(muted)
    this.update({ muted })
  }

  setPlaybackRate(rate: number) {
    this.audio.setPlaybackRate(rate)
    this.update({ playbackRate: rate })
  }

  /** Seeks the current step's narration; only while it is playing or paused, so loading and the end-of-step dwell are untouched. */
  seek(seconds: number) {
    if (this.state.phase !== 'playing' && this.state.phase !== 'paused') return
    if (!Number.isFinite(seconds)) return
    this.audio.seek(Math.max(0, seconds))
  }

  /** Playback position of the current narration (see NarrationAudio.subscribeProgress). */
  getProgress = (): NarrationProgress => this.audio.getProgress()

  subscribeProgress = (listener: () => void) => this.audio.subscribeProgress(listener)

  /** Turning narration off stops the audio; play mode then continues on reading-time dwells. */
  setNarrationEnabled(enabled: boolean) {
    if (enabled === this.state.narrationEnabled) return
    this.update({ narrationEnabled: enabled })
    this.abandonStep()
    this.update({ phase: 'idle', failureReason: null })
    if (this.state.autoplay) this.startStep()
  }

  /** Stops all playback and pending work but stays usable (e.g. across React StrictMode's remount). */
  halt() {
    this.abandonStep()
    this.update({ autoplay: false, phase: 'idle' })
  }

  dispose() {
    this.disposed = true
    this.abandonStep()
    this.listeners.clear()
  }

  // --- step lifecycle ------------------------------------------------------------------------

  private loadedUrl: string | null = null

  private startStep() {
    this.abandonStep()
    const step = this.steps[this.state.index]
    if (!step) return
    const generation = this.generation

    if (!this.state.narrationEnabled || !this.source) {
      this.update({ phase: 'unavailable', failureReason: null })
      this.scheduleSilentAdvance(step)
      return
    }

    const known = this.state.narrations[step.id]
    if (known?.status === 'READY' && known.audioUrl) {
      this.playUrl(known.audioUrl, generation)
      return
    }
    if (known && !known.narratable) {
      this.update({ phase: 'unavailable', failureReason: null })
      this.scheduleSilentAdvance(step)
      return
    }

    this.update({ phase: 'loading', failureReason: null })
    this.source.ensure(step.id).then(
      (narration) => {
        if (generation !== this.generation) return
        this.mergeNarrations([narration])
        this.handleNarration(step, narration, generation)
      },
      (error: unknown) => {
        if (generation !== this.generation) return
        this.fail(step, error instanceof Error ? error.message : 'Could not load narration')
      },
    )
  }

  private handleNarration(step: PlayerStep, narration: StepNarration, generation: number) {
    if (narration.status === 'READY' && narration.audioUrl) {
      this.generatingSince = null
      this.playUrl(narration.audioUrl, generation)
    } else if (!narration.narratable) {
      this.update({ phase: 'unavailable', failureReason: null })
      this.scheduleSilentAdvance(step)
    } else if (narration.status === 'FAILED') {
      this.generatingSince = null
      this.fail(step, narration.failureReason ?? 'Narration could not be generated')
    } else {
      // GENERATING (or a transient NOT_GENERATED/STALE right after a claim): wait for it.
      this.generatingSince ??= this.now()
      if (this.now() - this.generatingSince > this.generationTimeoutMs) {
        this.generatingSince = null
        this.fail(step, 'Narration is taking too long to generate')
        return
      }
      this.update({ phase: 'generating', failureReason: null })
      this.timer = this.setTimer(() => {
        this.timer = null
        void this.poll(step, generation)
      }, this.pollMs)
    }
  }

  private async poll(step: PlayerStep, generation: number) {
    if (!this.source) return
    let narrations: StepNarration[]
    try {
      narrations = await this.source.list()
    } catch {
      narrations = []
    }
    if (generation !== this.generation) return
    this.mergeNarrations(narrations)
    const current = this.state.narrations[step.id]
    if (current && (current.status === 'NOT_GENERATED' || current.status === 'STALE')) {
      // The generation died or the text changed under it: ask again (the backend dedupes).
      this.source.ensure(step.id).then(
        (narration) => {
          if (generation !== this.generation) return
          this.mergeNarrations([narration])
          this.handleNarration(step, narration, generation)
        },
        () => {
          if (generation === this.generation) this.handleNarration(step, current, generation)
        },
      )
      return
    }
    this.handleNarration(step, current ?? { stepId: step.id, status: 'GENERATING', narratable: true }, generation)
  }

  private playUrl(url: string, generation: number) {
    if (this.loadedUrl !== url) {
      this.audio.load(url)
      this.loadedUrl = url
    } else {
      this.audio.rewind()
    }
    this.update({ phase: 'loading', failureReason: null })
    this.audio.play().then(
      () => {
        if (generation !== this.generation) return
        this.update({ phase: 'playing' })
        this.prefetchNext()
      },
      (error: unknown) => {
        if (generation !== this.generation) return
        if (error instanceof Error && error.name === 'NotAllowedError') {
          this.update({ phase: 'blocked' })
          return
        }
        // AbortError means a newer load()/pause() replaced this play() — not a failure.
        if (error instanceof Error && error.name === 'AbortError') return
        this.fail(this.steps[this.state.index], 'The narration audio could not be played')
      },
    )
  }

  private resumeAudio() {
    const generation = this.generation
    this.audio.play().then(
      () => {
        if (generation === this.generation) this.update({ phase: 'playing' })
      },
      (error: unknown) => {
        if (generation !== this.generation) return
        if (error instanceof Error && error.name === 'NotAllowedError') this.update({ phase: 'blocked' })
        else this.fail(this.steps[this.state.index], 'The narration audio could not be played')
      },
    )
    this.update({ phase: 'playing' })
  }

  private handleEnded() {
    if (this.disposed || this.state.phase !== 'playing') return
    this.update({ phase: 'ended' })
    if (!this.state.autoplay) return
    const generation = this.generation
    this.timer = this.setTimer(() => {
      this.timer = null
      if (generation === this.generation) this.advance()
    }, this.dwellMs)
  }

  private handleAudioError() {
    if (this.disposed) return
    const phase = this.state.phase
    if (phase !== 'loading' && phase !== 'playing') return
    this.loadedUrl = null
    this.fail(this.steps[this.state.index], 'The narration audio could not be loaded')
  }

  private fail(step: PlayerStep | undefined, reason: string) {
    this.update({ phase: 'failed', failureReason: reason })
    if (step) this.scheduleSilentAdvance(step)
  }

  /** In play mode, a step without narration still advances, after enough time to read it. */
  private scheduleSilentAdvance(step: PlayerStep) {
    if (!this.state.autoplay) return
    const words = (step.text ?? '').trim().split(/\s+/).filter(Boolean).length
    const ms = Math.max(this.silentDwellMinMs, (words * this.silentMsPerWord) / this.state.playbackRate)
    const generation = this.generation
    this.cancelTimer()
    this.timer = this.setTimer(() => {
      this.timer = null
      if (generation === this.generation) this.advance()
    }, ms)
  }

  private advance() {
    if (this.state.index >= this.steps.length - 1) {
      this.abandonStep()
      this.update({ autoplay: false, finished: true, phase: 'idle' })
      return
    }
    this.abandonStep()
    this.update({ index: this.state.index + 1, phase: 'idle', failureReason: null })
    this.startStep()
  }

  /** While a step plays, warm up the next one so it is usually READY by the time we get there. */
  private prefetchNext() {
    if (!this.source || !this.state.autoplay || !this.state.narrationEnabled) return
    const nextStep = this.steps[this.state.index + 1]
    if (!nextStep || this.prefetched.has(nextStep.id)) return
    const known = this.state.narrations[nextStep.id]
    if (known && (known.status === 'READY' || known.status === 'GENERATING' || !known.narratable)) return
    this.prefetched.add(nextStep.id)
    this.source.ensure(nextStep.id).then(
      (narration) => this.mergeNarrations([narration]),
      () => {
        // The step will be requested again when it is reached.
        this.prefetched.delete(nextStep.id)
      },
    )
  }

  /** Stops whatever the current step was doing and invalidates its pending async work. */
  private abandonStep() {
    this.generation += 1
    this.generatingSince = null
    this.cancelTimer()
    this.audio.stop()
    this.loadedUrl = null
  }

  private cancelTimer() {
    if (this.timer != null) {
      this.clearTimer(this.timer)
      this.timer = null
    }
  }

  private currentNarration(): StepNarration | undefined {
    const step = this.steps[this.state.index]
    return step ? this.state.narrations[step.id] : undefined
  }

  private isAudioLoadedFor(url: string) {
    return this.loadedUrl === url
  }

  private mergeNarrations(narrations: StepNarration[]) {
    if (narrations.length === 0) return
    const merged = { ...this.state.narrations }
    for (const narration of narrations) merged[narration.stepId] = narration
    this.update({ narrations: merged })
  }

  private update(patch: Partial<NarrationPlayerState>) {
    if (this.disposed) return
    this.state = { ...this.state, ...patch }
    for (const listener of this.listeners) listener()
  }
}
