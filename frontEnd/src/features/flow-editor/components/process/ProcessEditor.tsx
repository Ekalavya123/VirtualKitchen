import type { ReactNode } from 'react'
import ProcessCanvas from './ProcessCanvas'
import '../../styles/flow-editor.css'
import type { ProcessBreadcrumbEntry } from '../../../../types/process'

interface ProcessEditorProps {
  recipeId: number
  processId: number
  breadcrumbAncestors: ProcessBreadcrumbEntry[]
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  onOpenSubprocess: (subprocessId: number, currentProcessName: string) => void
  onBack?: () => void
  /** Passed straight through to ProcessCanvas — see its own doc comment. */
  sidebarHeader?: ReactNode
}

/**
 * Thin wrapper around ProcessCanvas, mirroring FlowEditor.tsx's relationship
 * to FlowCanvas.tsx. The actual navigation trail (breadcrumb) is owned by
 * App.tsx's ProcessEditorRoute as router state — this wrapper and
 * ProcessCanvas just render it and report navigation intents upward.
 */
export default function ProcessEditor({
  recipeId,
  processId,
  breadcrumbAncestors,
  onNavigateToList,
  onNavigateToAncestor,
  onOpenSubprocess,
  onBack,
  sidebarHeader,
}: ProcessEditorProps) {
  return (
    <div className="h-full w-full">
      <ProcessCanvas
        recipeId={recipeId}
        processId={processId}
        breadcrumbAncestors={breadcrumbAncestors}
        onNavigateToList={onNavigateToList}
        onNavigateToAncestor={onNavigateToAncestor}
        onOpenSubprocess={onOpenSubprocess}
        onBack={onBack}
        sidebarHeader={sidebarHeader}
      />
    </div>
  )
}
