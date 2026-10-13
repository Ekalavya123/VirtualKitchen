import { createContext, useContext } from 'react'

/**
 * What RecipeProcessCanvas offers the process sidebar it hosts (RecipeEditorView's
 * RecipeProcessSidebar, passed in as `sidebarHeader`): adding a node to the open process — the
 * sidebar's "+ Add" menu — and collapsing the sidebar to its rail. Plain React Context, so the
 * sidebar element can still be built by RecipeEditorView; null outside a canvas (e.g. before any
 * process exists), where the sidebar simply has no Step/Condition items and no collapse button.
 */
export type ProcessSidebarActions = {
  onAddStep: () => void
  onAddCondition: () => void
  onCollapse: () => void
}

const ProcessSidebarActionsContext = createContext<ProcessSidebarActions | null>(null)

export const ProcessSidebarActionsProvider = ProcessSidebarActionsContext.Provider

export const useProcessSidebarActions = () => useContext(ProcessSidebarActionsContext)
