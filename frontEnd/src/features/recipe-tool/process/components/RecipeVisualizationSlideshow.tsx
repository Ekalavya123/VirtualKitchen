import { useEffect, useMemo, useState } from 'react'
import NarrationControls from '../narration/NarrationControls'
import { useNarrationPlayer } from '../narration/useNarrationPlayer'
import './RecipeVisualizationSlideshow.css'

export type SlideshowStep = {
  id: string
  title: string
  description?: string
  imageUrl?: string
  stepNumber?: number
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
    () => steps.map((step) => ({ id: step.id, text: [step.title, step.description].filter(Boolean).join('. ') })),
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
              <div className="recipe-slideshow-body">
                {currentStep?.imageUrl ? (
                  <img className="recipe-slideshow-image" src={currentStep.imageUrl} alt={currentStep.title} />
                ) : (
                  <div className="recipe-slideshow-placeholder">
                    <span className="recipe-slideshow-placeholder-icon">🖼️</span>
                    <span>Image not generated yet</span>
                  </div>
                )}
              </div>

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
