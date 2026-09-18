import { createContext, useContext } from 'react'
import type { GlobalIngredient } from '../../../api'
import type { Process } from '../../../types/process'

/**
 * Read-only lookup data a STEP node needs to display its Action On
 * information (ingredient names, subprocess names/output) directly on the
 * canvas, without opening the properties panel. Provided by ProcessCanvas
 * (which already loads both lists for the panel) and consumed by
 * ProcessStepNode — plain React Context, not a new state-management
 * framework, and not written to by anything downstream.
 */
export type ProcessGraphContextValue = {
  ingredientCatalog: GlobalIngredient[]
  /** SUBPROCESS documents this step could reference — same recipe, excluding MAIN and the process currently open. */
  availableSubprocesses: Process[]
}

const ProcessGraphContext = createContext<ProcessGraphContextValue>({
  ingredientCatalog: [],
  availableSubprocesses: [],
})

export const ProcessGraphProvider = ProcessGraphContext.Provider

export const useProcessGraphContext = () => useContext(ProcessGraphContext)
