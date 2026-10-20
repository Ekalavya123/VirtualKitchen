import { useState } from 'react'
import { useRecipeSession } from '../session/sessionContext'
import { buildRecipePresentation } from '../view/model/recipeView'
import { buildCookModeSlides } from '../view/model/cookModeSlides'
import RecipeVisualizationSlideshow, { type SlideshowStep } from '../walkthrough/RecipeVisualizationSlideshow'

type RecipePreviewButtonProps = {
  recipeId: number
}

/**
 * "Preview": the whole recipe as Cook mode walks it (every preparation, then the main process), from
 * either tab. Unlike Cook mode it never refuses an unfinished recipe — it shows whatever is there now,
 * unsaved edits included, so the owner can see how the recipe reads while still building it. Narration
 * comes from the saved steps, so the owner's pending edits are saved first (as the process
 * visualization does).
 */
export default function RecipePreviewButton({ recipeId }: RecipePreviewButtonProps) {
  const session = useRecipeSession()
  const [preview, setPreview] = useState<{ steps: SlideshowStep[]; ready: Promise<boolean> } | null>(null)

  if (!session || session.loading || session.loadError) return null

  const openPreview = () => {
    const presentation = buildRecipePresentation(session.getProcesses())
    const ready = session.canEdit
      ? session.saveNow().then((result) => result.ok, () => false)
      : Promise.resolve(true)
    setPreview({ steps: buildCookModeSlides(presentation.sections), ready })
  }

  return (
    <>
      <button
        type="button"
        onClick={openPreview}
        title="Preview the whole recipe step by step, as Cook mode shows it"
        aria-label="Preview recipe"
        style={{
          padding: '7px 12px', borderRadius: 8, flexShrink: 0, whiteSpace: 'nowrap',
          border: '1px solid var(--flow-border)', background: 'var(--flow-surface)',
          color: 'var(--flow-text-muted)', fontSize: 12, fontWeight: 700, cursor: 'pointer',
        }}
      >
        <span aria-hidden>👁</span>
        <span className="hidden sm:inline"> Preview</span>
      </button>

      {preview && (
        <RecipeVisualizationSlideshow
          steps={preview.steps}
          recipeId={recipeId}
          processId={null}
          narrationReady={preview.ready}
          heading="👁 Recipe preview"
          onClose={() => setPreview(null)}
        />
      )}
    </>
  )
}
