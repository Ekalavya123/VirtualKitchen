import { useEffect, useState } from 'react'
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
  onClose: () => void
}

const AUTO_PLAY_INTERVAL_MS = 3500

export default function RecipeVisualizationSlideshow({ steps, onClose }: RecipeVisualizationSlideshowProps) {
  const [index, setIndex] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)

  const hasSteps = steps.length > 0
  const currentStep = hasSteps ? steps[Math.min(index, steps.length - 1)] : undefined

  const goToPrevious = () => setIndex((current) => Math.max(0, current - 1))
  const goToNext = () => setIndex((current) => Math.min(steps.length - 1, current + 1))

  useEffect(() => {
    if (!isPlaying || !hasSteps) return

    const timer = window.setInterval(() => {
      setIndex((current) => {
        if (current >= steps.length - 1) {
          return 0
        }
        return current + 1
      })
    }, AUTO_PLAY_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [isPlaying, hasSteps, steps.length])

  return (
    <div className="flow-canvas-export-modal-overlay" onClick={onClose}>
      <div className="recipe-slideshow-modal" onClick={(event) => event.stopPropagation()}>
        <div className="recipe-slideshow-header">
          <div className="recipe-slideshow-title">🎬 Recipe Visualization</div>
          <button type="button" className="recipe-slideshow-close-btn" onClick={onClose}>✕</button>
        </div>

        {!hasSteps ? (
          <div className="recipe-slideshow-empty">
            No recipe steps yet. Add steps to the flow to preview them here.
          </div>
        ) : (
          <>
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

            <div className="recipe-slideshow-controls">
              <button
                type="button"
                className="recipe-slideshow-control-btn"
                onClick={goToPrevious}
                disabled={index === 0}
              >
                ⏮ Previous
              </button>
              <button
                type="button"
                className="recipe-slideshow-control-btn recipe-slideshow-play-btn"
                onClick={() => setIsPlaying((current) => !current)}
              >
                {isPlaying ? '⏸ Pause' : '▶ Play'}
              </button>
              <button
                type="button"
                className="recipe-slideshow-control-btn"
                onClick={goToNext}
                disabled={index === steps.length - 1}
              >
                Next ⏭
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
