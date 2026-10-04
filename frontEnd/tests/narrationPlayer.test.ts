import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  NarrationPlayer,
  type NarrationAudio,
  type NarrationPlayerOptions,
  type NarrationSource,
  type PlayerStep,
} from '../src/features/recipe-tool/process/narration/narrationPlayer.ts'
import type { StepNarration } from '../src/types/narration.ts'
import { FakeClock, deferred, flushMicrotasks } from './fakeClock.ts'

/** Records what the player does to the audio element and lets the test fire its events. */
class FakeAudio implements NarrationAudio {
  src: string | null = null
  playing = false
  muted = false
  rate = 1
  rewinds = 0
  loads: string[] = []
  rejectNextPlay: Error | null = null
  private handlers: { onEnded: () => void; onError: () => void } | null = null

  load(url: string) {
    this.src = url
    this.loads.push(url)
  }
  play() {
    if (this.rejectNextPlay) {
      const error = this.rejectNextPlay
      this.rejectNextPlay = null
      return Promise.reject(error)
    }
    this.playing = true
    return Promise.resolve()
  }
  pause() {
    this.playing = false
  }
  rewind() {
    this.rewinds += 1
  }
  stop() {
    this.playing = false
    this.src = null
  }
  setMuted(muted: boolean) {
    this.muted = muted
  }
  setPlaybackRate(rate: number) {
    this.rate = rate
  }
  setHandlers(handlers: { onEnded: () => void; onError: () => void }) {
    this.handlers = handlers
  }
  /** The narration reached its end. */
  end() {
    this.playing = false
    this.handlers?.onEnded()
  }
  fail() {
    this.handlers?.onError()
  }
}

const ready = (stepId: string): StepNarration => ({
  stepId, status: 'READY', narratable: true, audioUrl: `https://audio.example/${stepId}.wav`,
})
const generating = (stepId: string): StepNarration => ({ stepId, status: 'GENERATING', narratable: true })
const missing = (stepId: string): StepNarration => ({ stepId, status: 'NOT_GENERATED', narratable: true })

/** A backend whose narration state the test sets per step; counts every call. */
class FakeSource implements NarrationSource {
  narrations = new Map<string, StepNarration>()
  ensureCalls: string[] = []
  listCalls = 0
  /** What `ensure` turns a step into (default: READY immediately). */
  onEnsure: (stepId: string) => StepNarration = (stepId) => ready(stepId)

  async list() {
    this.listCalls += 1
    return Array.from(this.narrations.values())
  }
  async ensure(stepId: string) {
    this.ensureCalls.push(stepId)
    const current = this.narrations.get(stepId)
    const result = current?.status === 'READY' ? current : this.onEnsure(stepId)
    this.narrations.set(stepId, result)
    return result
  }
}

const STEPS: PlayerStep[] = [
  { id: 'a', text: 'Heat the oil' },
  { id: 'b', text: 'Add the onions' },
  { id: 'c', text: 'Stir until golden brown and fragrant' },
]

const setup = (overrides: Partial<NarrationPlayerOptions> = {}) => {
  const clock = new FakeClock()
  const audio = new FakeAudio()
  const source = new FakeSource()
  const player = new NarrationPlayer({
    steps: STEPS,
    source,
    audio,
    dwellMs: 500,
    silentDwellMinMs: 2000,
    silentMsPerWord: 400,
    pollMs: 1000,
    generationTimeoutMs: 10_000,
    now: () => clock.now,
    setTimer: clock.setTimer,
    clearTimer: clock.clearTimer,
    ...overrides,
  })
  return { clock, audio, source, player, state: () => player.getState() }
}

describe('NarrationPlayer', () => {
  it('plays each step and advances when its narration ends, stopping after the last step', async () => {
    const { clock, audio, source, player, state } = setup()
    for (const step of STEPS) source.narrations.set(step.id, ready(step.id))
    await player.init()

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'playing')
    assert.equal(audio.src, 'https://audio.example/a.wav')

    audio.end()
    assert.equal(state().phase, 'ended')
    assert.equal(state().index, 0, 'waits for the dwell before moving on')
    await clock.advance(500)
    assert.equal(state().index, 1)
    assert.equal(state().phase, 'playing')
    assert.equal(audio.src, 'https://audio.example/b.wav')

    audio.end()
    await clock.advance(500)
    audio.end()
    await clock.advance(500)

    assert.equal(state().index, 2)
    assert.equal(state().finished, true)
    assert.equal(state().autoplay, false)
    assert.deepEqual(source.ensureCalls, [], 'READY narration is reused, never re-requested')
  })

  it('pauses and resumes the same audio without reloading it', async () => {
    const { audio, source, player, state } = setup()
    source.narrations.set('a', ready('a'))
    await player.init()
    player.play()
    await flushMicrotasks()

    player.pause()
    assert.equal(state().phase, 'paused')
    assert.equal(state().autoplay, false)
    assert.equal(audio.playing, false)

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'playing')
    assert.equal(audio.loads.length, 1, 'resume continues the loaded audio')
  })

  it('replays the current narration from the start', async () => {
    const { audio, source, player, state } = setup()
    source.narrations.set('a', ready('a'))
    await player.init()
    player.play()
    await flushMicrotasks()
    audio.end()

    player.replay()
    await flushMicrotasks()
    assert.equal(state().phase, 'playing')
    assert.equal(audio.rewinds, 1)
    assert.equal(audio.loads.length, 1)
  })

  it('keeps advancing while muted, because muted audio still ends', async () => {
    const { clock, audio, source, player, state } = setup()
    for (const step of STEPS) source.narrations.set(step.id, ready(step.id))
    await player.init()
    player.setMuted(true)
    player.play()
    await flushMicrotasks()

    assert.equal(audio.muted, true)
    assert.equal(state().phase, 'playing')
    audio.end()
    await clock.advance(500)
    assert.equal(state().index, 1)
  })

  it('changes speed on the audio element only, without any backend call', async () => {
    const { audio, source, player, state } = setup()
    source.narrations.set('a', ready('a'))
    await player.init()
    player.play()
    await flushMicrotasks()
    const listCalls = source.listCalls
    const ensureCalls = [...source.ensureCalls] // includes the prefetch of the next step

    player.setPlaybackRate(1.2)
    await flushMicrotasks()

    assert.equal(audio.rate, 1.2)
    assert.equal(state().playbackRate, 1.2)
    assert.equal(state().phase, 'playing')
    assert.equal(source.listCalls, listCalls)
    assert.deepEqual(source.ensureCalls, ensureCalls)
    assert.equal(audio.loads.length, 1, 'the same audio keeps playing; nothing is regenerated or reloaded')
  })

  it('requests missing narration, polls while it generates, then plays it', async () => {
    const { clock, audio, source, player, state } = setup()
    source.onEnsure = generating
    await player.init()

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'generating')
    assert.deepEqual(source.ensureCalls, ['a'])

    await clock.advance(1000)
    assert.equal(state().phase, 'generating', 'still generating after the first poll')

    source.narrations.set('a', ready('a'))
    await clock.advance(1000)
    assert.equal(state().phase, 'playing')
    assert.equal(audio.src, 'https://audio.example/a.wav')
    assert.deepEqual(source.ensureCalls, ['a', 'b'], 'only the next step is prefetched, once')
  })

  it('falls back to a reading-time dwell when narration failed', async () => {
    const { clock, source, player, state } = setup()
    source.onEnsure = (stepId) => ({ stepId, status: 'FAILED', narratable: true, failureReason: 'quota exceeded' })
    await player.init()

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'failed')
    assert.equal(state().failureReason, 'quota exceeded')

    await clock.advance(1999)
    assert.equal(state().index, 0)
    await clock.advance(1)
    assert.equal(state().index, 1, 'a failed step never stalls the slideshow')
  })

  it('gives up on generation that takes too long', async () => {
    const { clock, source, player, state } = setup()
    source.onEnsure = generating
    await player.init()
    player.play()
    await flushMicrotasks()

    await clock.advance(12_000)
    assert.equal(state().phase === 'failed' || state().index > 0, true)
  })

  it('stops the current audio on next/previous and narrates the new step in play mode', async () => {
    const { audio, source, player, state } = setup()
    for (const step of STEPS) source.narrations.set(step.id, ready(step.id))
    await player.init()
    player.play()
    await flushMicrotasks()

    player.next()
    await flushMicrotasks()
    assert.equal(state().index, 1)
    assert.equal(audio.src, 'https://audio.example/b.wav')
    assert.equal(state().phase, 'playing')

    player.previous()
    await flushMicrotasks()
    assert.equal(state().index, 0)
    assert.equal(audio.src, 'https://audio.example/a.wav')
  })

  it('only shows the step when browsing outside play mode, and generates nothing', async () => {
    const { audio, source, player, state } = setup()
    await player.init()

    player.next()
    await flushMicrotasks()

    assert.equal(state().index, 1)
    assert.equal(state().phase, 'idle')
    assert.equal(audio.src, null)
    assert.deepEqual(source.ensureCalls, [])
  })

  it('ignores a late narration answer for a step the user already left', async () => {
    const { audio, source, player, state } = setup()
    const slow = deferred<StepNarration>()
    source.ensure = async (stepId: string) => {
      source.ensureCalls.push(stepId)
      return stepId === 'a' ? slow.promise : ready(stepId)
    }
    await player.init()
    player.play()
    await flushMicrotasks()

    player.next()
    await flushMicrotasks()
    slow.resolve(ready('a'))
    await flushMicrotasks()

    assert.equal(state().index, 1)
    assert.equal(audio.src, 'https://audio.example/b.wav')
  })

  it('reports a browser autoplay block and resumes on the next Play', async () => {
    const { audio, source, player, state } = setup()
    source.narrations.set('a', ready('a'))
    await player.init()
    const blocked = new Error('play() failed because the user did not interact')
    blocked.name = 'NotAllowedError'
    audio.rejectNextPlay = blocked

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'blocked')

    player.toggle()
    await flushMicrotasks()
    assert.equal(state().phase, 'playing')
  })

  it('treats a step without text as unavailable and moves on after the dwell', async () => {
    const { clock, source, player, state } = setup()
    source.narrations.set('a', { stepId: 'a', status: 'NOT_GENERATED', narratable: false })
    await player.init()

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'unavailable')
    assert.deepEqual(source.ensureCalls, [])
    await clock.advance(2000)
    assert.equal(state().index, 1)
  })

  it('runs silently with narration off, and never calls the backend for it', async () => {
    const { clock, audio, source, player, state } = setup({ initial: { narrationEnabled: false } })
    source.narrations.set('a', missing('a'))
    await player.init()

    player.play()
    await flushMicrotasks()
    assert.equal(state().phase, 'unavailable')
    assert.equal(audio.src, null)
    await clock.advance(2000)
    assert.equal(state().index, 1)
    assert.deepEqual(source.ensureCalls, [])
  })

  it('works without a narration source (unsaved process) as a timed slideshow', async () => {
    const { clock, player, state } = setup({ source: null })
    await player.init()
    player.play()
    await flushMicrotasks()

    assert.equal(state().phase, 'unavailable')
    await clock.advance(2000)
    assert.equal(state().index, 1)
  })

  it('fails over to the dwell when the audio file cannot be loaded', async () => {
    const { clock, audio, source, player, state } = setup()
    source.narrations.set('a', ready('a'))
    await player.init()
    player.play()
    await flushMicrotasks()

    audio.fail()
    assert.equal(state().phase, 'failed')
    await clock.advance(2000)
    assert.equal(state().index, 1)
  })

  it('restarts from the first step when played again after finishing', async () => {
    const { clock, audio, source, player, state } = setup()
    for (const step of STEPS) source.narrations.set(step.id, ready(step.id))
    await player.init()
    player.goTo(2)
    player.play()
    await flushMicrotasks()
    audio.end()
    await clock.advance(500)
    assert.equal(state().finished, true)

    player.play()
    await flushMicrotasks()
    assert.equal(state().index, 0)
    assert.equal(state().phase, 'playing')
  })
})
