import { useEffect, useMemo, useState } from 'react'
import NarrationControls from '../narration/NarrationControls'
import type { NarrationPhase } from '../narration/narrationPlayer'
import { useNarrationPlayer } from '../narration/useNarrationPlayer'
import AnimatedInstruction, { type InstructionRevealMode, type ProgressSource } from './AnimatedInstruction'
import './RecipeVisualizationSlideshow.css'

export type SlideshowStep = {
  id: string
  title: string
  /** The step's own instruction sentence (its action description), shown as the slide's main text. */
  instruction?: string
  /** Supporting details (targets, timing, expected result) shown under the title. */
  description?: string
  imageUrl?: string
  stepNumber?: number
}

/** How the instruction follows the current step's narration phase (see AnimatedInstruction). */
const revealModeFor = (phase: NarrationPhase): InstructionRevealMode => {
  switch (phase) {
    case 'playing':
    case 'paused':
      return 'audio'
    case 'loading':
      // The narration is about to start; the text then follows it from the first word.
      return 'hidden'
    case 'ended':
    case 'generating':
    case 'blocked':
      // Nothing to follow (yet): keep the whole step readable.
      return 'full'
    default:
      // idle / unavailable / failed: no narration to follow, so a short reading reveal.
      return 'timed'
  }
}

type StepStageProps = {
  step: SlideshowStep
  /** The narration script from the backend: exactly what the audio says, so it wins over the step's own text. */
  narrationText?: string | null
  revealMode: InstructionRevealMode
  paused: boolean
  progress: ProgressSource
  narrationDurationSeconds?: number | null
}

/**
 * One step's visual area. The instruction is always shown; the image is an enhancement on top of
 * it: until the image has actually loaded, the text fills the stage, and once it has, the image
 * fades in and the text moves to a caption overlay. Image state never touches narration.
 */
function StepStage({ step, narrationText, revealMode, paused, progress, narrationDurationSeconds }: StepStageProps) {
  const imageUrl = step.imageUrl
  const [image, setImage] = useState<{ url: string; status: 'loaded' | 'error' } | null>(null)
  const imageStatus = !imageUrl ? 'none' : image?.url === imageUrl ? image.status : 'loading'
  const showImage = imageStatus === 'loaded'
  // Until the narration state has loaded (or when a step has none), fall back to the step's own text.
  const text = narrationText?.trim() || step.instruction?.trim() || step.title.trim() || 'Untitled step'

  return (
    <div className={`recipe-slideshow-body ${showImage ? 'recipe-slideshow-body-image' : 'recipe-slideshow-body-text'}`}>
      {imageUrl && imageStatus !== 'error' && (
        <img
          key={imageUrl}
          className={`recipe-slideshow-image${showImage ? ' is-loaded' : ''}`}
          src={imageUrl}
          alt={step.title}
          onLoad={() => setImage({ url: imageUrl, status: 'loaded' })}
          onError={() => setImage({ url: imageUrl, status: 'error' })}
        />
      )}
      <AnimatedInstruction
        // A different text (e.g. the narration script arriving) restarts the reveal cleanly.
        key={text}
        text={text}
        mode={revealMode}
        paused={paused}
        progress={progress}
        fallbackDurationSeconds={narrationDurationSeconds}
        variant={showImage ? 'overlay' : 'full'}
      />
      {!showImage && (
        <span className="recipe-slideshow-media-status">
          {imageStatus === 'loading' ? 'Image loading…' : imageStatus === 'error' ? 'Image unavailable' : '🖼️ Image not generated yet'}
        </span>
      )}
    </div>
  )
}

type RecipeVisualizationSlideshowProps = {
  steps: SlideshowStep[]
  recipeId: number
  processId: number
  /** Settles once the editor's pending edits are saved; narration waits for it (see useNarrationPlayer). */
  narrationReady?: Promise<boolean> | null
  onClose: () => void
}

/**
 * Step-by-step walkthrough of a process. In play mode each step is narrated aloud and the next
 * step follows when its narration ends (steps without narration stay up for a reading-time dwell).
 * All playback rules live in NarrationPlayer; this component only renders its state.
 */
export default function RecipeVisualizationSlideshow({
  steps,
  recipeId,
  processId,
  narrationReady,
  onClose,
}: RecipeVisualizationSlideshowProps) {
  const playerSteps = useMemo(
    () => steps.map((step) => ({
      id: step.id,
      text: [step.title, step.instruction, step.description].filter(Boolean).join('. '),
    })),
    [steps],
  )
  const { player, state, unavailable, syncWarning } = useNarrationPlayer({
    recipeId,
    processId,
    steps: playerSteps,
    ready: narrationReady,
  })

  const hasSteps = steps.length > 0
  const index = Math.min(state.index, Math.max(0, steps.length - 1))
  const currentStep = hasSteps ? steps[index] : undefined

  // Slide direction follows the index change, whoever caused it (buttons or auto-advance).
  const [view, setView] = useState({ index, direction: 'next' as 'next' | 'prev' })
  if (view.index !== index) {
    setView({ index, direction: index > view.index ? 'next' : 'prev' })
  }

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      if (target && ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName)) return
      // A focused button already activates on Space; handling it here too would toggle twice.
      if (event.key === ' ' && target?.tagName === 'BUTTON') return
      if (event.key === 'ArrowRight') player.next()
      else if (event.key === 'ArrowLeft') player.previous()
      else if (event.key === ' ') {
        event.preventDefault()
        player.toggle()
      } else if (event.key === 'Escape') onClose()
      else return
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [player, onClose])

  const isPlaying = state.autoplay && state.phase !== 'blocked'
  const playLabel = isPlaying ? '⏸ Pause' : state.finished ? '↺ Play again' : '▶ Play'

  return (
    <div className="flow-canvas-export-modal-overlay" onClick={onClose}>
      <div className="recipe-slideshow-modal" onClick={(event) => event.stopPropagation()}>
        <div className="recipe-slideshow-header">
          <div className="recipe-slideshow-title">🎬 Recipe Visualization</div>
          <button type="button" className="recipe-slideshow-close-btn" onClick={onClose} aria-label="Close">✕</button>
        </div>

        {!hasSteps ? (
          <div className="recipe-slideshow-empty">
            No recipe steps yet. Add steps to the flow to preview them here.
          </div>
        ) : (
          <>
            <div
              key={currentStep?.id ?? index}
              className={`recipe-slideshow-stage recipe-slideshow-stage-${view.direction}`}
            >
              {currentStep && (
                <StepStage
                  step={currentStep}
                  narrationText={state.narrations[currentStep.id]?.text}
                  revealMode={revealModeFor(state.phase)}
                  paused={state.phase === 'paused'}
                  progress={player}
                  narrationDurationSeconds={state.narrations[currentStep.id]?.durationSeconds}
                />
              )}

              <div className="recipe-slideshow-caption">
                <div className="recipe-slideshow-step-label">
                  Step {currentStep?.stepNumber ?? index + 1} of {steps.length}
                </div>
                <div className="recipe-slideshow-step-title">{currentStep?.title || 'Untitled step'}</div>
                {currentStep?.description && (
                  <div className="recipe-slideshow-step-description">{currentStep.description}</div>
                )}
              </div>
            </div>

            <div className="recipe-slideshow-controls">
              <button
                type="button"
                className="recipe-slideshow-control-btn"
                onClick={() => player.previous()}
                disabled={index === 0}
              >
                ⏮ Previous
              </button>
              <button
                type="button"
                className="recipe-slideshow-control-btn recipe-slideshow-play-btn"
                onClick={() => player.toggle()}
              >
                {playLabel}
              </button>
              <button
                type="button"
                className="recipe-slideshow-control-btn"
                onClick={() => player.next()}
                disabled={index === steps.length - 1}
              >
                Next ⏭
              </button>
            </div>

            <NarrationControls
              player={player}
              state={state}
              stepId={currentStep?.id}
              unavailable={unavailable}
              syncWarning={syncWarning}
            />
          </>
        )}
      </div>
    </div>
  )
}
