import { createContext, useContext } from 'react'
import type { Process } from '../../../../types/process'
import { EMPTY_STEP_OUTPUT_GRAPH, type StepOutputGraph } from '../model/recipeStepOutputs'
import { DEFAULT_HANDLE_SIDES, type HandleSides } from '../model/processLayout'

/**
 * Read-only lookup data a STEP node needs to display its Action On
 * information (subprocess names) directly on the canvas, without opening
 * the properties panel. Ingredient names don't need this — they come from
 * the ingredient catalog (catalog/ingredientCatalog.ts, loaded from the
 * database before the editor renders), importable directly wherever needed. Provided by RecipeProcessCanvas (which already loads
 * the recipe's process list for the panel) and consumed by RecipeStepNode —
 * plain React Context, not a new state-management framework, and not
 * written to by anything downstream.
 */
export type RecipeProcessGraphContextValue = {
  /** SUBPROCESS documents this step could reference — same recipe, excluding MAIN and the process currently open. */
  availableSubprocesses: Process[]
  /**
   * STEP node ids of the current process, in the same order RecipeProcessCanvas/RecipeProcessTopBar number
   * and list them (array order — mirrors how the step dropdown and slideshow already number
   * steps) — lets a STEP node compute "step N" for its own badge without prop-drilling the whole
   * node list down to every card.
   */
  stepOrder: string[]
  /** The current process's step-output sources and graph reachability — resolves a referenced step's live Expected Output label on each card. */
  stepOutputGraph: StepOutputGraph
  /**
   * Reports a resize gesture's start/end back to RecipeProcessCanvas so exactly one undo entry is
   * recorded per completed resize (never per intermediate resize tick) — `NodeResizeControl`'s own
   * onResizeStart/onResizeEnd live inside each node component, not RecipeProcessCanvas itself, so this is
   * the extension point that lets the canvas's history stack learn about them. Undefined (a no-op)
   * when a node renders outside a RecipeProcessGraphProvider.
   */
  onNodeResizeStart?: (nodeId: string) => void
  onNodeResizeEnd?: (nodeId: string) => void
  /**
   * Which side each node's handles sit on, derived from where its neighbours are on the canvas
   * (model/processLayout.ts's getHandleSides) — never stored. A node without an entry uses
   * DEFAULT_HANDLE_SIDES (see useNodeHandleSides).
   */
  handleSides?: ReadonlyMap<string, HandleSides>
}

const RecipeProcessGraphContext = createContext<RecipeProcessGraphContextValue>({
  availableSubprocesses: [],
  stepOrder: [],
  stepOutputGraph: EMPTY_STEP_OUTPUT_GRAPH,
})

export const RecipeProcessGraphProvider = RecipeProcessGraphContext.Provider

export const useRecipeProcessGraphContext = () => useContext(RecipeProcessGraphContext)

/** This node's handle sides (DEFAULT_HANDLE_SIDES when the canvas hasn't derived any). */
export const useNodeHandleSides = (nodeId: string | null): HandleSides => {
  const { handleSides } = useContext(RecipeProcessGraphContext)
  return (nodeId != null ? handleSides?.get(nodeId) : undefined) ?? DEFAULT_HANDLE_SIDES
}
