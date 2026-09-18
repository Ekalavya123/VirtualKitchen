import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import type { ProcessNode } from '../../../types/process'

/**
 * Shares each process's *current, in-memory* STEP/CONDITION nodes across the
 * Recipe Tool — written by ProcessCanvas on every nodes/edges change (not
 * just on Save), read by anything that needs to reflect unsaved edits
 * immediately (e.g. the Ingredients view's Action On derivation). Keyed by
 * processId so edits already made to one process (MAIN or any subprocess)
 * during this visit stay visible even after navigating to another one —
 * ProcessCanvas is the only writer, this context never talks to the backend
 * itself. Plain React Context + a ref-backed map, not a new state
 * -management framework.
 */
export type ProcessLiveGraphContextValue = {
  /** The given process's current in-memory nodes, or undefined if it hasn't been loaded/edited yet this visit (callers should fall back to the last-saved server copy). */
  getLiveNodes: (processId: number) => ProcessNode[] | undefined
  /** Called by ProcessCanvas whenever its nodes change, including right after load, so this always reflects what's currently on the canvas. */
  setLiveNodes: (processId: number, nodes: ProcessNode[]) => void
  /**
   * Bumped on every `setLiveNodes` call. The store itself lives in a ref (so reading it doesn't
   * re-render anything), so consumers that derive something from `getLiveNodes` must depend on
   * this value in their own `useMemo`/`useEffect` — depending on the context value alone wouldn't
   * detect changes, since `getLiveNodes`/`setLiveNodes` are themselves stable across updates.
   */
  version: number
}

const ProcessLiveGraphContext = createContext<ProcessLiveGraphContextValue | null>(null)

export function ProcessLiveGraphProvider({ children }: { children: ReactNode }) {
  const storeRef = useRef(new Map<number, ProcessNode[]>())
  const [version, setVersion] = useState(0)

  const getLiveNodes = useCallback((processId: number) => storeRef.current.get(processId), [])

  const setLiveNodes = useCallback((processId: number, nodes: ProcessNode[]) => {
    storeRef.current.set(processId, nodes)
    setVersion((v) => v + 1)
  }, [])

  const value = useMemo<ProcessLiveGraphContextValue>(() => ({ getLiveNodes, setLiveNodes, version }), [getLiveNodes, setLiveNodes, version])

  return <ProcessLiveGraphContext.Provider value={value}>{children}</ProcessLiveGraphContext.Provider>
}

/** Returns null outside a provider (e.g. the standalone /process/:processId route) — callers should fall back to server-loaded data in that case. */
export const useProcessLiveGraph = () => useContext(ProcessLiveGraphContext)
