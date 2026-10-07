import type { ReactNode } from 'react'
import RecipeProcessCanvas from './RecipeProcessCanvas'
import '../../styles/recipe-tool.css'
import type { RecipeProcessBreadcrumbEntry } from '../../../../types/recipe'

interface RecipeProcessEditorProps {
  recipeId: number
  processId: number
  breadcrumbAncestors: RecipeProcessBreadcrumbEntry[]
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  onOpenSubprocess: (subprocessId: number, currentProcessName: string) => void
  onBack?: () => void
  /** Passed straight through to RecipeProcessCanvas — see its own doc comment. */
  sidebarHeader?: ReactNode
  onSelectedNodeChange?: (nodeId: string | null) => void
  highlightedNodeIds?: ReadonlySet<string>
}

/**
 * Thin wrapper around RecipeProcessCanvas. The actual navigation trail (breadcrumb) is owned by
 * App.tsx's RecipeProcessEditorRoute as router state — this wrapper and
 * RecipeProcessCanvas just render it and report navigation intents upward.
 */
export default function RecipeProcessEditor({
  recipeId,
  processId,
  breadcrumbAncestors,
  onNavigateToList,
  onNavigateToAncestor,
  onOpenSubprocess,
  onBack,
  sidebarHeader,
  onSelectedNodeChange,
  highlightedNodeIds,
}: RecipeProcessEditorProps) {
  return (
    <div className="h-full w-full">
      <RecipeProcessCanvas
        recipeId={recipeId}
        processId={processId}
        breadcrumbAncestors={breadcrumbAncestors}
        onNavigateToList={onNavigateToList}
        onNavigateToAncestor={onNavigateToAncestor}
        onOpenSubprocess={onOpenSubprocess}
        onBack={onBack}
        sidebarHeader={sidebarHeader}
        onSelectedNodeChange={onSelectedNodeChange}
        highlightedNodeIds={highlightedNodeIds}
      />
    </div>
  )
}
