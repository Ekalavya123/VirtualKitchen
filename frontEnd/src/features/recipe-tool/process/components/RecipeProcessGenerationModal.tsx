import { useState } from 'react'
import '../styles/RecipeProcessCanvas.css'

type RecipeProcessGenerationModalProps = {
  onClose: () => void
  onGenerate: (recipeText: string) => Promise<void>
  isGenerating: boolean
  progress: { percent: number; stageLabel: string } | null
  /** True when generating will replace the recipe's current MAIN process content — shown as an inline warning, not a separate confirm dialog, so the user sees it right where they're about to act. */
  willReplaceMain: boolean
}

const PLACEHOLDER = `Describe your recipe here...

Example:

Make chicken curry. Marinate the chicken with curd and spices. Cut onions and
tomatoes separately. Fry the onions, add tomatoes, then add the marinated
chicken and cook.`

/**
 * "Generate with AI" entry point of the Recipe Process — reuses the same modal chrome as
 * RecipeProcessCanvas's Export dialog (`.flow-canvas-export-modal*`) and the progress-bar styling
 * (`.recipe-builder-progress*`), both from RecipeProcessCanvas.css, rather than introducing a new
 * visual pattern. It's a modal because generation is an occasional action, not a constant
 * companion to editing.
 */
export default function RecipeProcessGenerationModal({ onClose, onGenerate, isGenerating, progress, willReplaceMain }: RecipeProcessGenerationModalProps) {
  const [recipeText, setRecipeText] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleGenerate = async () => {
    const trimmed = recipeText.trim()
    if (!trimmed) {
      setErrorMessage('Enter a recipe before generating.')
      return
    }
    setErrorMessage(null)
    try {
      await onGenerate(trimmed)
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Unable to generate this recipe right now.')
    }
  }

  return (
    <div className="flow-canvas-export-modal-overlay" onClick={() => !isGenerating && onClose()}>
      <div className="flow-canvas-export-modal" onClick={(event) => event.stopPropagation()}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', borderBottom: '1px solid var(--flow-border)' }}>
          <div>
            <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--flow-text)' }}>✨ Generate Recipe with AI</div>
            <div style={{ fontSize: 11, color: 'var(--flow-text-subtle)', marginTop: 2 }}>
              Creates a MAIN process (and subprocesses where meaningful) from your description — nothing is saved until you click Save.
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isGenerating}
            style={{ width: 30, height: 30, borderRadius: 7, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-subtle)', cursor: isGenerating ? 'not-allowed' : 'pointer', fontSize: 15 }}
          >
            ✕
          </button>
        </div>

        <div style={{ padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 12 }}>
          {willReplaceMain && (
            <div style={{ fontSize: 11.5, color: 'var(--flow-warning)', background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', borderRadius: 8, padding: '8px 10px' }}>
              ⚠ This recipe already has a MAIN process. Generating will replace its content (existing subprocesses are kept; new ones may be added). Nothing is saved until you click Save.
            </div>
          )}

          <textarea
            value={recipeText}
            onChange={(event) => setRecipeText(event.target.value)}
            className="recipe-builder-textarea"
            placeholder={PLACEHOLDER}
            rows={10}
            disabled={isGenerating}
            style={{ minHeight: 180 }}
          />

          {errorMessage && (
            <div className="recipe-builder-status recipe-builder-status-error" role="alert">{errorMessage}</div>
          )}

          {isGenerating && progress && (
            <div className="recipe-builder-progress" role="progressbar" aria-valuenow={progress.percent} aria-valuemin={0} aria-valuemax={100} title={progress.stageLabel}>
              <div className="recipe-builder-progress-track">
                <div className="recipe-builder-progress-fill" style={{ width: `${progress.percent}%` }} />
              </div>
              <span className="recipe-builder-progress-label">{progress.percent}%</span>
            </div>
          )}

          <div style={{ display: 'flex', gap: 8 }}>
            <button
              type="button"
              onClick={() => void handleGenerate()}
              disabled={isGenerating}
              style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12.5, fontWeight: 700, cursor: isGenerating ? 'wait' : 'pointer' }}
            >
              {isGenerating ? (progress?.stageLabel ?? 'Generating…') : 'Generate'}
            </button>
            <button
              type="button"
              onClick={onClose}
              disabled={isGenerating}
              style={{ padding: '8px 16px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-muted)', fontSize: 12.5, fontWeight: 700, cursor: isGenerating ? 'not-allowed' : 'pointer' }}
            >
              Cancel
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
