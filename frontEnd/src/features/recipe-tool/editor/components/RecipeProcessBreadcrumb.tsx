import type { ReactNode } from 'react'
import '../../styles/recipe-tool.css'
import type { ProcessType } from '../../../../types/process'
import type { RecipeProcessBreadcrumbEntry } from '../../../../types/recipe'

type RecipeProcessBreadcrumbProps = {
  /** The recipe's name for the trail's root — "Recipe" where it isn't known (the standalone process route). */
  recipeName?: string
  /** Ancestors from the recipe's process list down to (but not including) the process currently open, root-first. */
  ancestors: RecipeProcessBreadcrumbEntry[]
  currentName: string
  processType?: ProcessType
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  /** The trail's last segment — the node selector. */
  trailing?: ReactNode
}

const Separator = () => <span className="recipe-breadcrumb-separator" aria-hidden>/</span>

/**
 * "Where am I?" for the Process Editor: Recipe / (parent processes /) Process / Node. Purely a
 * display + click-to-navigate component — the trail itself is owned by whoever renders the editor
 * (RecipeEditorView, or App.tsx's RecipeProcessEditorRoute as router state).
 */
export default function RecipeProcessBreadcrumb({
  recipeName,
  ancestors,
  currentName,
  processType,
  onNavigateToList,
  onNavigateToAncestor,
  trailing,
}: RecipeProcessBreadcrumbProps) {
  return (
    <nav aria-label="Editing context" className="recipe-breadcrumb">
      <button
        type="button"
        onClick={onNavigateToList}
        className="recipe-breadcrumb-crumb is-recipe"
        title={recipeName ? `Recipe: ${recipeName} — go to its main process` : 'All processes for this recipe'}
      >
        <span aria-hidden>🍳</span>
        <span className="recipe-breadcrumb-text">{recipeName || 'Recipe'}</span>
      </button>

      {ancestors.map((ancestor, index) => (
        <span key={ancestor.processId} className="recipe-breadcrumb-segment is-ancestor">
          <Separator />
          <button
            type="button"
            onClick={() => onNavigateToAncestor(index)}
            className="recipe-breadcrumb-crumb"
            title={`Back to ${ancestor.name}`}
          >
            <span className="recipe-breadcrumb-text">{ancestor.name}</span>
          </button>
        </span>
      ))}

      <Separator />
      <span className="recipe-breadcrumb-current" title={`${processType === 'SUBPROCESS' ? 'Subprocess' : 'Process'}: ${currentName}`} aria-current="page">
        <span className="recipe-breadcrumb-text">{currentName}</span>
        {processType && (
          <span className={`recipe-breadcrumb-type is-${processType.toLowerCase()}`}>
            {processType === 'MAIN' ? 'Main' : 'Sub'}
          </span>
        )}
      </span>

      {trailing && (
        <>
          <Separator />
          {trailing}
        </>
      )}
    </nav>
  )
}
