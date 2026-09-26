import { createContext, useContext } from 'react'
import type { Process } from '../../../types/process'

/**
 * Read-only lookup data a STEP node needs to display its Action On
 * information (subprocess names) directly on the canvas, without opening
 * the properties panel. Ingredient names don't need this — they come from
 * the app's static UI catalog (catalog/ingredientCatalog.ts), importable
 * directly wherever needed. Provided by ProcessCanvas (which already loads
 * the recipe's process list for the panel) and consumed by ProcessStepNode —
 * plain React Context, not a new state-management framework, and not
 * written to by anything downstream.
 */
export type ProcessGraphContextValue = {
  /** SUBPROCESS documents this step could reference — same recipe, excluding MAIN and the process currently open. */
  availableSubprocesses: Process[]
  /**
   * STEP node ids of the current process, in the same order ProcessCanvas/ProcessTopBar number
   * and list them (array order — mirrors how the step dropdown and slideshow already number
   * steps) — lets a STEP node compute "step N" for its own badge without prop-drilling the whole
   * node list down to every card.
   */
  stepOrder: string[]
  /**
   * Reports a resize gesture's start/end back to ProcessCanvas so exactly one undo entry is
   * recorded per completed resize (never per intermediate resize tick) — `NodeResizeControl`'s own
   * onResizeStart/onResizeEnd live inside each node component, not ProcessCanvas itself, so this is
   * the extension point that lets the canvas's history stack learn about them. Undefined (a no-op)
   * outside a Process canvas, e.g. the legacy FlowCanvas's shared ConditionNode.
   */
  onNodeResizeStart?: (nodeId: string) => void
  onNodeResizeEnd?: (nodeId: string) => void
}

const ProcessGraphContext = createContext<ProcessGraphContextValue>({
  availableSubprocesses: [],
  stepOrder: [],
})

export const ProcessGraphProvider = ProcessGraphContext.Provider

export const useProcessGraphContext = () => useContext(ProcessGraphContext)
