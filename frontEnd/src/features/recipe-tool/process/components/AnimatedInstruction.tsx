import { useCallback, useMemo, useSyncExternalStore, type CSSProperties } from 'react'
import type { NarrationProgress } from '../narration/narrationPlayer'
import { readingRevealMs, splitWords, visibleWordCount } from '../narration/wordReveal'

/**
 * How the instruction is revealed:
 * - audio: follows the narration's playback position (pause, resume and seeking follow for free)
 * - hidden: laid out but not shown yet (narration is about to start)
 * - timed: a short CSS-only reading reveal, for steps without narration to follow
 * - full: everything shown at once
 */
export type InstructionRevealMode = 'audio' | 'hidden' | 'timed' | 'full'

/** The narration playback position, as NarrationPlayer exposes it. */
export type ProgressSource = {
  getProgress(): NarrationProgress
  subscribeProgress(listener: () => void): () => void
}

type AnimatedInstructionProps = {
  text: string
  mode: InstructionRevealMode
  /** Audio mode only: the narration is paused, so the words still to come are shown faintly. */
  paused?: boolean
  progress: ProgressSource
  /** Narration length known from the backend, used until the audio element reports its own. */
  fallbackDurationSeconds?: number | null
  /** overlay: over the bottom of the step image; full: fills the stage when there is no image. */
  variant: 'overlay' | 'full'
}

const LONG_TEXT_WORDS = 40
const noSubscription = () => () => {}

/**
 * The current step's instruction, revealed word by word. Presentation only: it reads the
 * narration position but never controls the audio. Every word is always rendered (unrevealed ones
 * are transparent), so the text never reflows while it is revealed. Re-renders only when the
 * number of visible words changes, not on every progress tick.
 */
export default function AnimatedInstruction({
  text,
  mode,
  paused = false,
  progress,
  fallbackDurationSeconds,
  variant,
}: AnimatedInstructionProps) {
  const words = useMemo(() => splitWords(text), [text])
  const total = words.length
  const followsAudio = mode === 'audio'

  const getVisibleCount = useCallback(() => {
    if (!followsAudio) return 0
    const { currentTime, duration } = progress.getProgress()
    return visibleWordCount(currentTime, duration, total, fallbackDurationSeconds)
  }, [followsAudio, progress, total, fallbackDurationSeconds])
  const audioVisible = useSyncExternalStore(followsAudio ? progress.subscribeProgress : noSubscription, getVisibleCount)

  if (total === 0) return null

  const visible = mode === 'full' || mode === 'timed' ? total : mode === 'hidden' ? 0 : audioVisible
  const wordMs = mode === 'timed' ? readingRevealMs(total) / total : 0
  const classes = [
    'recipe-instruction',
    `recipe-instruction-${variant}`,
    `recipe-instruction-mode-${mode}`,
    followsAudio && paused ? 'recipe-instruction-paused' : '',
    total > LONG_TEXT_WORDS ? 'recipe-instruction-long' : '',
  ].filter(Boolean).join(' ')

  return (
    <p className={classes} style={{ '--instruction-word-ms': `${Math.round(wordMs)}ms` } as CSSProperties}>
      <span className="recipe-instruction-sr">{text}</span>
      <span aria-hidden="true">
        {words.map((word, index) => {
          const revealed = index < visible
          const current = followsAudio && index === visible - 1
          return (
            <span key={index}>
              <span
                className={`recipe-instruction-word${revealed ? ' is-revealed' : ''}${current ? ' is-current' : ''}`}
                style={mode === 'timed' ? { animationDelay: `${Math.round(index * wordMs)}ms` } : undefined}
              >
                {word}
              </span>
              {index < total - 1 ? ' ' : ''}
            </span>
          )
        })}
      </span>
    </p>
  )
}
