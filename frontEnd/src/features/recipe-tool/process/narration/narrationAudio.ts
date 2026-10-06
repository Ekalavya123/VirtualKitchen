import type { NarrationAudio, NarrationProgress } from './narrationPlayer'

/** Element events after which the playback position may differ from what listeners last saw. */
const PROGRESS_EVENTS = ['timeupdate', 'seeked', 'durationchange', 'loadedmetadata', 'play', 'pause', 'ended', 'emptied'] as const

/**
 * The browser implementation of {@link NarrationAudio}: one reusable, detached HTMLAudioElement.
 * Speed is applied here (playbackRate, pitch preserved by the browser), so changing it never
 * touches the generated audio.
 */
export function createHtmlNarrationAudio(): NarrationAudio & { dispose(): void } {
  const element = new Audio()
  element.preload = 'auto'
  let handlers: { onEnded: () => void; onError: () => void } | null = null
  const progressListeners = new Set<() => void>()
  let frame: number | null = null

  const onEnded = () => handlers?.onEnded()
  // An empty src (after stop) also raises "error" in some browsers; only a real source counts.
  const onError = () => {
    if (element.getAttribute('src')) handlers?.onError()
  }
  element.addEventListener('ended', onEnded)
  element.addEventListener('error', onError)

  const notifyProgress = () => {
    for (const listener of progressListeners) listener()
  }

  // "timeupdate" only fires a few times a second; while audio plays (and someone is listening)
  // listeners are also polled once per frame. They decide themselves whether anything changed.
  const stopFrames = () => {
    if (frame != null) {
      cancelAnimationFrame(frame)
      frame = null
    }
  }
  const tick = () => {
    frame = null
    notifyProgress()
    if (!element.paused && !element.ended && progressListeners.size > 0) frame = requestAnimationFrame(tick)
  }
  const onProgressEvent = (event: Event) => {
    notifyProgress()
    if (event.type === 'play') {
      if (frame == null && progressListeners.size > 0) frame = requestAnimationFrame(tick)
    } else if (event.type === 'pause' || event.type === 'ended' || event.type === 'emptied') {
      stopFrames()
    }
  }
  for (const type of PROGRESS_EVENTS) element.addEventListener(type, onProgressEvent)

  const clear = () => {
    element.pause()
    if (element.getAttribute('src')) {
      element.removeAttribute('src')
      element.load()
    }
  }

  return {
    load(url) {
      element.src = url
      element.load()
    },
    play: () => element.play(),
    pause: () => element.pause(),
    rewind() {
      element.currentTime = 0
    },
    stop: clear,
    setMuted(muted) {
      element.muted = muted
    },
    setPlaybackRate(rate) {
      // A new src resets playbackRate to defaultPlaybackRate, so set both.
      element.defaultPlaybackRate = rate
      element.playbackRate = rate
    },
    setHandlers(next) {
      handlers = next
    },
    seek(seconds) {
      if (!element.getAttribute('src')) return
      const duration = element.duration
      element.currentTime = Number.isFinite(duration) ? Math.min(seconds, duration) : seconds
    },
    getProgress: (): NarrationProgress => ({
      currentTime: element.getAttribute('src') ? element.currentTime : 0,
      duration: element.getAttribute('src') ? element.duration : Number.NaN,
    }),
    subscribeProgress(listener) {
      progressListeners.add(listener)
      if (frame == null && !element.paused && !element.ended) frame = requestAnimationFrame(tick)
      return () => {
        progressListeners.delete(listener)
        if (progressListeners.size === 0) stopFrames()
      }
    },
    dispose() {
      handlers = null
      stopFrames()
      progressListeners.clear()
      element.removeEventListener('ended', onEnded)
      element.removeEventListener('error', onError)
      for (const type of PROGRESS_EVENTS) element.removeEventListener(type, onProgressEvent)
      clear()
    },
  }
}
