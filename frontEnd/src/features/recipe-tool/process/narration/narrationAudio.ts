import type { NarrationAudio } from './narrationPlayer'

/**
 * The browser implementation of {@link NarrationAudio}: one reusable, detached HTMLAudioElement.
 * Speed is applied here (playbackRate, pitch preserved by the browser), so changing it never
 * touches the generated audio.
 */
export function createHtmlNarrationAudio(): NarrationAudio & { dispose(): void } {
  const element = new Audio()
  element.preload = 'auto'
  let handlers: { onEnded: () => void; onError: () => void } | null = null

  const onEnded = () => handlers?.onEnded()
  // An empty src (after stop) also raises "error" in some browsers; only a real source counts.
  const onError = () => {
    if (element.getAttribute('src')) handlers?.onError()
  }
  element.addEventListener('ended', onEnded)
  element.addEventListener('error', onError)

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
    dispose() {
      handlers = null
      element.removeEventListener('ended', onEnded)
      element.removeEventListener('error', onError)
      clear()
    },
  }
}
