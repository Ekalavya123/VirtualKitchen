import { useState } from 'react'
import { formatTotalMinutes, type RecipePresentation } from '../../recipe-tool/presentation/model/recipePresentation'
import RecipeImage from '../../recipe-tool/presentation/components/RecipeImage'
import '../../recipe-tool/presentation/styles/recipe-process.css'

type ProcessPreviewProps = {
  /** The order's recipe snapshot, through the Recipe Process model (see model/orderPresentation.ts). */
  presentation: RecipePresentation
}

/**
 * A compact, read-only walk through the recipe being ordered: one collapsible section per stage
 * (subprocesses first, the main process last — the Recipe Process page's order), each step with
 * its number, its instruction and, when one was generated, its image.
 */
export default function ProcessPreview({ presentation }: ProcessPreviewProps) {
  // The first stage starts open; the rest are a click away.
  const [openKeys, setOpenKeys] = useState<Set<string> | null>(null)
  const isOpen = (key: string, index: number) => (openKeys ? openKeys.has(key) : index === 0)

  if (presentation.sections.length === 0) {
    return <div className="ro-empty">This recipe has no steps yet.</div>
  }

  const toggle = (key: string) => {
    setOpenKeys((current) => {
      const first = presentation.sections[0]
      const next = new Set(current ?? (first ? [first.key] : []))
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  const allOpen = presentation.sections.every((section, index) => isOpen(section.key, index))

  return (
    <div className="ro-preview">
      <div className="ro-preview-toolbar">
        <span className="ro-muted">
          {presentation.sections.length} {presentation.sections.length === 1 ? 'stage' : 'stages'} · {presentation.stepCount}{' '}
          {presentation.stepCount === 1 ? 'step' : 'steps'}
        </span>
        <button
          type="button"
          className="ro-link-btn"
          onClick={() => setOpenKeys(allOpen ? new Set() : new Set(presentation.sections.map((section) => section.key)))}
        >
          {allOpen ? 'Collapse all' : 'Expand all'}
        </button>
      </div>

      {presentation.sections.map((section, index) => {
        const open = isOpen(section.key, index)
        const panelId = `ro-preview-${section.key.replace(/[^a-zA-Z0-9_-]/g, '-')}`
        return (
          <section key={section.key} className={`ro-stage${open ? ' ro-stage-open' : ''}`}>
            <h4 className="ro-stage-heading">
              <button type="button" aria-expanded={open} aria-controls={panelId} onClick={() => toggle(section.key)}>
                <span className="ro-stage-chevron" aria-hidden>▸</span>
                <span className="ro-stage-title">{section.title}</span>
                <span className="ro-stage-count">
                  {section.stepCount} {section.stepCount === 1 ? 'step' : 'steps'}
                </span>
              </button>
            </h4>
            {open && (
              <ol id={panelId} className="ro-stage-items">
                {section.items.map((item) =>
                  item.kind === 'step' ? (
                    <li key={item.key} className="ro-preview-step">
                      <span className="ro-preview-number" aria-label={`Step ${item.number}`}>{item.number}</span>
                      <div className="ro-preview-text">
                        <p>{item.instructionText}</p>
                        {item.explanation && <p className="ro-muted">{item.explanation}</p>}
                      </div>
                      {item.imageUrl && (
                        <div className="ro-preview-media">
                          <RecipeImage src={item.imageUrl} alt={`Step ${item.number}`} fallbackIcon={item.icon} />
                        </div>
                      )}
                    </li>
                  ) : (
                    <li key={item.key} className="ro-preview-check">
                      <span aria-hidden>❔</span> Check: {item.question || 'check before continuing'}
                    </li>
                  ),
                )}
              </ol>
            )}
          </section>
        )
      })}

      {presentation.totalMinutes != null && (
        <div className="ro-muted ro-preview-total">Timed steps add up to {formatTotalMinutes(presentation.totalMinutes)}.</div>
      )}
    </div>
  )
}
