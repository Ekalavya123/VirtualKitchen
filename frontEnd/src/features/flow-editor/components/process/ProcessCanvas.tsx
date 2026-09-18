import { useCallback, useEffect, useRef, useState } from 'react'
import type { Dispatch, ReactNode, SetStateAction } from 'react'
import {
  ReactFlow,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  BackgroundVariant,
  PanOnScrollMode,
  type ReactFlowInstance,
  type Node,
  type Edge,
  type Connection,
  type IsValidConnection,
  type NodeChange,
  type EdgeChange,
  type Viewport,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'

import { nodeTypes } from '../../nodes/nodeTypes'
import {
  FLOW_NODE_TYPES,
  isAnyStepNode,
  isConditionNode,
  isProcessStepNode,
  type FlowNodeType,
} from '../../model/flowNodeModel'
import {
  createConditionNode,
  createFlowDataPayload,
  getFlowEdgePresentation,
  normalizeFlowEdges,
  normalizeFlowNode,
  applyStepOrConditionFieldUpdate,
} from '../canvas/FlowCanvas.helpers'
import { normalizeConditionNodeData, type FlowViewport } from '../../../../types/recipeFlow'
import {
  buildProcessUpdateRequest,
  createProcessStepNode,
  processToFlowData,
} from '../../adapters/processFlowAdapter'
import {
  applyProcessStepFieldUpdate,
  getProcessStepDurationLabel,
  normalizeProcessStepNodeData,
  withProcessStepActionOnIngredients,
  withProcessStepActionOnProcesses,
  withProcessStepVisualization,
  type ActionOnIngredient,
} from '../../model/processStepData'
import { getIngredientById } from '../../catalog/ingredientCatalog'
import { ProcessGraphProvider } from '../../context/ProcessGraphContext'
import { useRecipeSession, type RecipeSessionContextValue } from '../../../recipe-tool/context/RecipeSessionContext'
import type { Process, ProcessBreadcrumbEntry, ProcessVisualizationStepResult } from '../../../../types/process'
import { useNotifications } from '../../../../shared/components/notifications/NotificationProvider'
import { ProcessVisualizationApi } from '../../../../api'
import PropertiesPanel from '../toolbar/PropertiesPanel'
import ProcessStepPanel from './ProcessStepPanel'
import ProcessTopBar from './ProcessTopBar'
import RecipeVisualizationSlideshow, { type SlideshowStep } from '../canvas/RecipeVisualizationSlideshow'
import '../../styles/flow-editor.css'
import '../sidebar/Sidebar.css'
import '../toolbar/PropertiesPanel.css'
import '../canvas/FlowCanvas.css'

const VISUALIZATION_JOB_POLL_MS = 2000
const sleep = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms))

type HistoryEntry = { nodes: Node[]; edges: Edge[] }

type ProcessCanvasProps = {
  recipeId: number
  processId: number
  /** Ancestors from the recipe's process list down to this process's parent, root-first (empty for MAIN or a directly-opened process). */
  breadcrumbAncestors: ProcessBreadcrumbEntry[]
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  /** Called when the user chooses to open a subprocess referenced from a STEP's Action On (see ProcessStepPanel's "Open" button) — a Process graph never contains a node for this, so there's no double-click-to-open path anymore. */
  onOpenSubprocess: (subprocessId: number, currentProcessName: string) => void
  onBack?: () => void
  /**
   * The Recipe Tool's process list (see RecipeProcessView), rendered as this component's whole
   * left column when present — filling it entirely, not split with anything else, now that node
   * add/navigation live in ProcessTopBar and the old Tool Options panel has nothing left to show.
   * Omitted for the standalone `/process/:processId` route, where there's no sibling process list;
   * that route simply has no left column at all (the canvas takes the full width instead).
   */
  sidebarHeader?: ReactNode
}

/**
 * A recipe is one unified editing session — MAIN and every SUBPROCESS are
 * views of the same in-memory snapshot (RecipeSessionContext), not
 * independent flows. This outer component owns only what should genuinely
 * survive a process switch: the resizable panel layout (widths/collapsed
 * state). Everything that is legitimately *per-process* — the canvas's
 * nodes/edges, selection, undo/redo history, zoom/viewport — lives in
 * ProcessCanvasContent below, which is deliberately remounted (via `key`)
 * whenever `processId` changes.
 *
 * This is *not* the same kind of remount the Recipe Tool used to do: that
 * remounted this whole component (topbar, panels, layout *and* data) and
 * re-fetched the process from the backend, which is what lost unsaved edits
 * and made switching feel like opening a separate flow. Here, the process's
 * data was already loaded once into RecipeSessionContext (no fetch on
 * switch), every edit is continuously pushed into that shared session as it
 * happens (not only on unmount), and only the small, genuinely per-process
 * slice of local UI state (selection, undo stack, zoom) resets — exactly
 * the "process-local" behavior the brief allows for undo/redo. Switching
 * back to a previously-edited process re-seeds straight from the session,
 * so its unsaved changes are still there.
 */
export default function ProcessCanvas(props: ProcessCanvasProps) {
  const session = useRecipeSession()
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [sidebarWidth, setSidebarWidth] = useState(260)
  const [propsCollapsed, setPropsCollapsed] = useState(true)
  const [propsWidth, setPropsWidth] = useState(280)

  // Undo/redo history, keyed by processId, lives here rather than in ProcessCanvasContent (which
  // deliberately remounts fresh via `key={processId}` below) so switching Process A -> B -> A within
  // this session doesn't lose A's history — everything else genuinely per-process (nodes/edges,
  // selection, zoom) still resets on that remount; only these two stacks survive it.
  const [historyByProcess, setHistoryByProcess] = useState<Record<number, HistoryEntry[]>>({})
  const [futureByProcess, setFutureByProcess] = useState<Record<number, HistoryEntry[]>>({})

  const { processId } = props
  const setProcessHistory = useCallback<Dispatch<SetStateAction<HistoryEntry[]>>>((action) => {
    setHistoryByProcess((prev) => {
      const current = prev[processId] ?? []
      const next = typeof action === 'function' ? (action as (h: HistoryEntry[]) => HistoryEntry[])(current) : action
      return { ...prev, [processId]: next }
    })
  }, [processId])
  const setProcessFuture = useCallback<Dispatch<SetStateAction<HistoryEntry[]>>>((action) => {
    setFutureByProcess((prev) => {
      const current = prev[processId] ?? []
      const next = typeof action === 'function' ? (action as (h: HistoryEntry[]) => HistoryEntry[])(current) : action
      return { ...prev, [processId]: next }
    })
  }, [processId])

  if (!session || session.loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading process…
      </div>
    )
  }

  const process = session.getProcess(props.processId)

  if (session.loadError || !process) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center gap-3" style={{ color: 'var(--flow-text-muted)' }}>
        <div>{session.loadError ?? 'This process could not be loaded.'}</div>
        {props.onBack && (
          <button onClick={props.onBack} style={{ padding: '8px 14px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', cursor: 'pointer' }}>
            ← Back
          </button>
        )}
      </div>
    )
  }

  const availableSubprocesses = session.getProcesses().filter((candidate) => candidate.type === 'SUBPROCESS' && candidate.id !== props.processId)

  return (
    <ProcessCanvasContent
      key={props.processId}
      {...props}
      process={process}
      availableSubprocesses={availableSubprocesses}
      session={session}
      sidebarCollapsed={sidebarCollapsed}
      setSidebarCollapsed={setSidebarCollapsed}
      sidebarWidth={sidebarWidth}
      setSidebarWidth={setSidebarWidth}
      propsCollapsed={propsCollapsed}
      setPropsCollapsed={setPropsCollapsed}
      propsWidth={propsWidth}
      setPropsWidth={setPropsWidth}
      history={historyByProcess[props.processId] ?? []}
      setHistory={setProcessHistory}
      future={futureByProcess[props.processId] ?? []}
      setFuture={setProcessFuture}
    />
  )
}

type ProcessCanvasContentProps = ProcessCanvasProps & {
  process: Process
  availableSubprocesses: Process[]
  session: RecipeSessionContextValue
  sidebarCollapsed: boolean
  setSidebarCollapsed: Dispatch<SetStateAction<boolean>>
  sidebarWidth: number
  setSidebarWidth: Dispatch<SetStateAction<number>>
  propsCollapsed: boolean
  setPropsCollapsed: Dispatch<SetStateAction<boolean>>
  propsWidth: number
  setPropsWidth: Dispatch<SetStateAction<number>>
  /** Owned by the outer ProcessCanvas (keyed by processId), not this remounting component — see its own comment. */
  history: HistoryEntry[]
  setHistory: Dispatch<SetStateAction<HistoryEntry[]>>
  future: HistoryEntry[]
  setFuture: Dispatch<SetStateAction<HistoryEntry[]>>
}

function ProcessCanvasContent({
  recipeId,
  processId,
  process,
  availableSubprocesses,
  session,
  breadcrumbAncestors,
  onNavigateToList,
  onNavigateToAncestor,
  onOpenSubprocess,
  onBack,
  sidebarHeader,
  sidebarCollapsed,
  setSidebarCollapsed,
  sidebarWidth,
  setSidebarWidth,
  propsCollapsed,
  setPropsCollapsed,
  propsWidth,
  setPropsWidth,
  history,
  setHistory,
  future,
  setFuture,
}: ProcessCanvasContentProps) {
  const { notifySuccess, notifyError } = useNotifications()

  // Seeded once, synchronously, from the already-loaded session — no fetch, no loading flash.
  // Recomputed on every render of this instance, but only the *first* render's value is actually
  // used (React ignores later arguments to useNodesState/useEdgesState) since this whole component
  // remounts fresh (via `key={processId}` in ProcessCanvas above) whenever the selected process
  // changes, rather than resetting this state in place.
  const initialFlowData = processToFlowData(process)
  const [nodes, setNodes, onNodesChange] = useNodesState(initialFlowData.nodes.map(normalizeFlowNode))
  const [edges, setEdges, onEdgesChange] = useEdgesState(normalizeFlowEdges(initialFlowData.edges))
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [zoomPercent, setZoomPercent] = useState(Math.round((initialFlowData.viewport?.zoom ?? 1) * 100))
  const [exportJson, setExportJson] = useState<string | null>(null)
  const [showSlideshow, setShowSlideshow] = useState(false)
  const [generatingVisuals, setGeneratingVisuals] = useState(false)
  const [generateVisualsStatus, setGenerateVisualsStatus] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  // Guards the polling loop below against setState-after-unmount — this instance remounts fresh
  // per process (key={processId} on the outer ProcessCanvas), so a job started just before
  // switching processes must stop touching this instance's state once it's gone. Reset on every
  // (re-)mount rather than declared once, matching FlowCanvas's own reasoning: React StrictMode's
  // dev-only mount->cleanup->mount cycle would otherwise run the cleanup once during the synthetic
  // first "unmount" and permanently latch this to true even though the component is actually mounted.
  const unmountedRef = useRef(false)
  useEffect(() => {
    unmountedRef.current = false
    return () => {
      unmountedRef.current = true
    }
  }, [])

  const reactFlowInstance = useRef<ReactFlowInstance<Node, Edge> | null>(null)
  const currentViewportRef = useRef<FlowViewport | null>(initialFlowData.viewport ?? null)
  const sidebarRef = useRef<HTMLDivElement | null>(null)
  const propsRef = useRef<HTMLDivElement | null>(null)
  const bodyRef = useRef<HTMLDivElement | null>(null)
  const reactFlowWrapperRef = useRef<HTMLDivElement | null>(null)

  // Always-current mirrors of `nodes`/`edges`, for callbacks that must read the latest state without
  // being recreated on every nodes/edges change (drag-start/resize-start snapshotting below fires
  // from gesture callbacks that are handed to React Flow / a node component once, not re-subscribed
  // every render).
  const nodesRef = useRef(nodes)
  const edgesRef = useRef(edges)
  useEffect(() => { nodesRef.current = nodes }, [nodes])
  useEffect(() => { edgesRef.current = edges }, [edges])

  // The canvas itself must never be resized away to nothing — every panel resizer caps its own
  // width against how much room the *other* panel + this floor are currently taking up, on top of
  // its own static max (mirrors FlowCanvas.tsx's getMaxPanelWidth).
  const MIN_CANVAS_WIDTH = 280
  const getMaxPanelWidth = useCallback((staticMax: number, otherPanelWidth: number, gaps: number) => {
    const containerWidth = bodyRef.current?.offsetWidth ?? window.innerWidth
    const dynamicMax = containerWidth - otherPanelWidth - gaps - MIN_CANVAS_WIDTH
    return Math.min(staticMax, dynamicMax)
  }, [])

  // Auto-collapse the properties panel when nothing is selected, expand it when a node is selected —
  // mirrors old FlowCanvas's behavior so the rail only takes up space while it has something to show.
  useEffect(() => {
    setPropsCollapsed(selectedNodeId == null)
  }, [selectedNodeId, setPropsCollapsed])

  // Publishes this process's current nodes/edges/viewport into the shared recipe session on every
  // *real* change — not just on Save — so switching to a different process (or the Ingredients tab)
  // always sees this process's latest unsaved edits. Must not push the data this instance was merely
  // seeded with (not a real edit): a naive "skip the first effect firing" ref flag is not safe here,
  // because React StrictMode's dev-only double-invoke of effects (mount -> cleanup -> mount, with no
  // cleanup returned here) flips such a flag on the *first* simulated mount, so the *second* one no
  // longer skips — reproduced live, it marked a freshly created, untouched process "unsaved"
  // immediately. Comparing against the exact array references this instance was seeded with is safe
  // under that double-invoke (state itself is not reset by it), and remains correct forever after:
  // once a real edit calls setNodes/setEdges, the reference changes permanently.
  const initialNodesRef = useRef(nodes)
  const initialEdgesRef = useRef(edges)
  useEffect(() => {
    if (nodes === initialNodesRef.current && edges === initialEdgesRef.current) return
    const flowData = createFlowDataPayload(nodes, edges, currentViewportRef.current ?? undefined)
    session.updateProcess(processId, buildProcessUpdateRequest(process.name, process.description, flowData))
    // eslint-disable-next-line react-hooks/exhaustive-deps -- process.name/description are read at push time via the closure, not a reactive dependency: this effect's job is to react to *canvas* edits (nodes/edges), and process identity doesn't change within one mounted instance (key={processId} above).
  }, [nodes, edges])

  /** Snapshots nodes/edges *before* a mutation, for Undo. Deliberately not called on every field
   * keystroke (only structural changes: add/delete/duplicate node, connect/remove edge, drag end) —
   * snapshotting per-keystroke would flood the stack without much undo value. */
  const pushHistorySnapshot = useCallback(() => {
    setHistory((h) => [...h, { nodes: structuredClone(nodes), edges: structuredClone(edges) }].slice(-50))
    setFuture([])
  }, [nodes, edges, setHistory, setFuture])

  const undo = useCallback(() => {
    if (history.length === 0) return
    const previous = history[history.length - 1]
    setFuture((f) => [{ nodes: structuredClone(nodes), edges: structuredClone(edges) }, ...f])
    setNodes(previous.nodes)
    setEdges(previous.edges)
    setHistory((h) => h.slice(0, -1))
  }, [history, nodes, edges, setNodes, setEdges, setHistory, setFuture])

  const redo = useCallback(() => {
    if (future.length === 0) return
    const next = future[0]
    setHistory((h) => [...h, { nodes: structuredClone(nodes), edges: structuredClone(edges) }])
    setNodes(next.nodes)
    setEdges(next.edges)
    setFuture((f) => f.slice(1))
  }, [future, nodes, edges, setNodes, setEdges, setHistory, setFuture])

  // Node move (drag) and resize each get their own start/end snapshot pair below instead — a
  // `remove` is the only structural node change left to catch here (add/connect/delete-edge already
  // snapshot explicitly at their own call sites).
  const handleNodesChange = useCallback((changes: NodeChange<Node>[]) => {
    if (changes.some((change) => change.type === 'remove')) pushHistorySnapshot()
    onNodesChange(changes)
  }, [onNodesChange, pushHistorySnapshot])

  const handleEdgesChange = useCallback((changes: EdgeChange<Edge>[]) => {
    if (changes.some((change) => change.type === 'remove')) pushHistorySnapshot()
    onEdgesChange(changes)
  }, [onEdgesChange, pushHistorySnapshot])

  /**
   * One undo entry per completed drag, not per mouse-move: `onNodeDragStart`/`onNodeDragStop` are
   * React Flow's own start/end pair for a node-move gesture (unlike position NodeChange events,
   * which fire continuously with `dragging: true` while the mouse moves and would have snapshotted
   * the *already-moved* position by the time a single terminal event was ever seen). If the node is
   * back at its starting position when the drag ends, no entry is recorded at all.
   */
  const dragSnapshotRef = useRef<{ nodeId: string; entry: HistoryEntry } | null>(null)
  const handleNodeDragStart = useCallback((_event: unknown, node: Node) => {
    dragSnapshotRef.current = {
      nodeId: node.id,
      entry: { nodes: structuredClone(nodesRef.current), edges: structuredClone(edgesRef.current) },
    }
  }, [])
  const handleNodeDragStop = useCallback((_event: unknown, node: Node) => {
    const snapshot = dragSnapshotRef.current
    dragSnapshotRef.current = null
    if (!snapshot || snapshot.nodeId !== node.id) return
    const before = snapshot.entry.nodes.find((n) => n.id === node.id)
    if (before && before.position.x === node.position.x && before.position.y === node.position.y) return
    setHistory((h) => [...h, snapshot.entry].slice(-50))
    setFuture([])
  }, [setHistory, setFuture])

  /**
   * Same one-entry-per-gesture treatment for resize, driven by ProcessGraphContext's
   * onNodeResizeStart/onNodeResizeEnd (NodeResizeControl's own start/end callbacks live inside each
   * node component, not here — see ProcessStepNode.tsx/ConditionNode.tsx) rather than the
   * `dimensions` NodeChange stream, which fires on every intermediate resize tick and would flood
   * history the same way raw position events would for dragging.
   */
  const resizeSnapshotRef = useRef<{ nodeId: string; entry: HistoryEntry } | null>(null)
  const handleNodeResizeStart = useCallback((nodeId: string) => {
    resizeSnapshotRef.current = {
      nodeId,
      entry: { nodes: structuredClone(nodesRef.current), edges: structuredClone(edgesRef.current) },
    }
  }, [])
  const handleNodeResizeEnd = useCallback((nodeId: string) => {
    const snapshot = resizeSnapshotRef.current
    resizeSnapshotRef.current = null
    if (!snapshot || snapshot.nodeId !== nodeId) return
    const before = snapshot.entry.nodes.find((n) => n.id === nodeId)
    const after = nodesRef.current.find((n) => n.id === nodeId)
    const beforeSize = { width: before?.width ?? before?.measured?.width, height: before?.height ?? before?.measured?.height }
    const afterSize = { width: after?.width ?? after?.measured?.width, height: after?.height ?? after?.measured?.height }
    if (beforeSize.width === afterSize.width && beforeSize.height === afterSize.height) return
    setHistory((h) => [...h, snapshot.entry].slice(-50))
    setFuture([])
  }, [setHistory, setFuture])

  const addNode = useCallback((nodeType: FlowNodeType) => {
    pushHistorySnapshot()
    const id = crypto.randomUUID()
    const position = { x: 420 + (nodes.length % 4) * 40, y: 140 + nodes.length * 50 }
    const newNode =
      nodeType === FLOW_NODE_TYPES.condition
        ? createConditionNode(id, position)
        : createProcessStepNode(id, position)

    setNodes((nds) => [...nds, newNode])
    setSelectedNodeId(id)
    setSelectedEdgeId(null)
  }, [nodes.length, setNodes, pushHistorySnapshot])

  const deleteNode = useCallback((id: string) => {
    pushHistorySnapshot()
    setNodes((nds) => nds.filter((node) => node.id !== id))
    setEdges((eds) => eds.filter((edge) => edge.source !== id && edge.target !== id))
    setSelectedNodeId((current) => (current === id ? null : current))
  }, [setNodes, setEdges, pushHistorySnapshot])

  const deleteEdge = useCallback((id: string) => {
    pushHistorySnapshot()
    setEdges((eds) => eds.filter((edge) => edge.id !== id))
    setSelectedEdgeId((current) => (current === id ? null : current))
  }, [setEdges, pushHistorySnapshot])

  const deleteSelected = useCallback(() => {
    if (selectedNodeId) deleteNode(selectedNodeId)
    else if (selectedEdgeId) deleteEdge(selectedEdgeId)
  }, [selectedNodeId, selectedEdgeId, deleteNode, deleteEdge])

  // Keyboard shortcuts (Delete/Backspace, Ctrl/Cmd+Z, Ctrl/Cmd+Y) — mirrors old FlowCanvas's global
  // listener so shortcuts work regardless of which element inside the canvas currently has focus,
  // not only while the React Flow pane itself is focused. Skips typing in an input/textarea, and is
  // scoped to specific key combos only, so it never interferes with browser/app navigation shortcuts.
  // This is the *only* keyboard-delete path — React Flow's own built-in one is disabled
  // (`deleteKeyCode={null}` below) so a single press can't be handled twice (once by us calling
  // deleteNode/deleteEdge directly, once by React Flow's own default removal going through
  // onNodesChange/onEdgesChange) and so edges — which React Flow tracks selection for independently
  // of our own `selectedNodeId` — are covered too, which the old node-only handler never was.
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const tag = (event.target as HTMLElement)?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA') return
      if (event.key === 'Delete' || event.key === 'Backspace') deleteSelected()
      if ((event.ctrlKey || event.metaKey) && event.key === 'z') { event.preventDefault(); undo() }
      if ((event.ctrlKey || event.metaKey) && event.key === 'y') { event.preventDefault(); redo() }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [deleteSelected, undo, redo])

  const duplicateNode = useCallback((id: string) => {
    pushHistorySnapshot()
    setNodes((nds) => {
      const original = nds.find((node) => node.id === id)
      if (!original) return nds
      return [
        ...nds,
        {
          ...structuredClone(original),
          id: crypto.randomUUID(),
          selected: false,
          position: { x: original.position.x + 24, y: original.position.y + 24 },
        },
      ]
    })
  }, [setNodes, pushHistorySnapshot])

  const updateNodeField = useCallback((nodeId: string, field: string, value: string) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? applyStepOrConditionFieldUpdate(node, field, value) : node)))

    if (field === 'condition.successLabel' || field === 'condition.failureLabel') {
      const sourceHandle = field === 'condition.successLabel' ? 'condition-yes' : 'condition-no'
      const fallbackLabel = field === 'condition.successLabel' ? 'Yes' : 'No'
      setEdges((eds) => eds.map((edge) =>
        edge.source === nodeId && edge.sourceHandle === sourceHandle
          ? { ...edge, label: value.trim() || fallbackLabel }
          : edge))
    }
  }, [setNodes, setEdges])

  const updateProcessStepField = useCallback((nodeId: string, field: string, value: string) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: applyProcessStepFieldUpdate(node.data, field, value) } : node)))
  }, [setNodes])

  const updateActionOnIngredients = useCallback((nodeId: string, ingredients: ActionOnIngredient[]) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withProcessStepActionOnIngredients(node.data, ingredients) } : node)))
  }, [setNodes])

  const updateActionOnProcesses = useCallback((nodeId: string, processIds: number[]) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withProcessStepActionOnProcesses(node.data, processIds) } : node)))
  }, [setNodes])

  const isValidConnection = useCallback<IsValidConnection<Edge>>((connectionOrEdge) => {
    const source = connectionOrEdge.source
    const target = connectionOrEdge.target
    if (!source || !target || source === target) return false

    const duplicate = edges.some((edge) =>
      edge.source === source &&
      edge.target === target &&
      (edge.sourceHandle ?? null) === (connectionOrEdge.sourceHandle ?? null) &&
      (edge.targetHandle ?? null) === (connectionOrEdge.targetHandle ?? null))

    return !duplicate
  }, [edges])

  const onConnect = useCallback((connection: Connection) => {
    pushHistorySnapshot()
    const sourceNode = nodes.find((node) => node.id === connection.source)
    let label: string | undefined

    if (sourceNode && isConditionNode(sourceNode)) {
      const condition = normalizeConditionNodeData(sourceNode.data).condition
      if (connection.sourceHandle === 'condition-yes') label = condition.successLabel
      if (connection.sourceHandle === 'condition-no') label = condition.failureLabel
    }

    const presentation = getFlowEdgePresentation(connection.sourceHandle, connection.targetHandle, label)
    setEdges((eds) => addEdge({ ...connection, id: crypto.randomUUID(), ...presentation }, eds))
  }, [nodes, setEdges, pushHistorySnapshot])

  const handleZoomIn = useCallback(() => {
    reactFlowInstance.current?.zoomIn()
  }, [])

  const handleZoomOut = useCallback(() => {
    reactFlowInstance.current?.zoomOut()
  }, [])

  const handleFitView = useCallback(() => {
    reactFlowInstance.current?.fitView({ padding: 0.12 })
  }, [])

  const handleViewportMove = useCallback((_event: unknown, viewport: Viewport) => {
    currentViewportRef.current = viewport
    setZoomPercent(Math.round(viewport.zoom * 100))
  }, [])

  // Suppressed exactly once: when this process had no saved viewport, `fitView` (below, on the
  // ReactFlow element) runs automatically on mount and fires one onMoveEnd of its own — that
  // shouldn't itself mark a freshly-opened, otherwise-untouched process dirty. Every onMoveEnd after
  // that (drag, scroll-zoom, pinch, or the topbar's Zoom/Fit View buttons) is a real view change and
  // is pushed into the session so it survives a process switch and is included in the next Save —
  // see the module-level architecture note on why pure pan/zoom was previously silently lost.
  const suppressNextMoveEndRef = useRef(initialFlowData.viewport == null)
  const handleViewportMoveEnd = useCallback((_event: unknown, viewport: Viewport) => {
    currentViewportRef.current = viewport
    setZoomPercent(Math.round(viewport.zoom * 100))
    if (suppressNextMoveEndRef.current) {
      suppressNextMoveEndRef.current = false
      return
    }
    session.updateProcess(processId, { viewport: { x: viewport.x, y: viewport.y, zoom: viewport.zoom } })
  }, [session, processId])

  const handleExport = useCallback(() => {
    const viewport = currentViewportRef.current ?? reactFlowInstance.current?.getViewport()
    const flowData = createFlowDataPayload(nodes, edges, viewport ?? undefined)
    const payload = buildProcessUpdateRequest(process.name, process.description, flowData)
    setExportJson(JSON.stringify({ recipeId, processId, type: process.type, ...payload }, null, 2))
  }, [nodes, edges, process, recipeId, processId])

  const downloadExportJson = useCallback(() => {
    if (!exportJson) return
    const blob = new Blob([exportJson], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `process-${processId}.json`
    link.click()
    URL.revokeObjectURL(url)
  }, [exportJson, processId])

  const copyExportJson = useCallback(() => {
    if (!exportJson) return
    void navigator.clipboard.writeText(exportJson)
  }, [exportJson])

  // Walkthrough of this process's steps, in flow order. Falls back to a text-only "story mode"
  // slide (the slideshow component already renders a placeholder) until a step has actually been
  // visualized — see generateVisuals below, which is what populates `visualization.imageUrl`.
  const buildSlideshowSteps = useCallback((): SlideshowStep[] => {
    const stepNodes = nodes.filter(isProcessStepNode)
    return stepNodes.map((node, index) => {
      const normalized = normalizeProcessStepNodeData(node.data)
      const { step } = normalized
      const ingredientNames = step.actionOn.ingredients.map((entry) => getIngredientById(entry.ingredientId).name)
      const subprocessNames = step.actionOn.processes
        .map((entry) => availableSubprocesses.find((candidate) => candidate.id === entry.processId)?.name)
        .filter((name): name is string => Boolean(name))
      const descriptionParts = [
        step.actionDescription,
        ingredientNames.length > 0 ? `On: ${ingredientNames.join(', ')}` : '',
        subprocessNames.length > 0 ? `Using: ${subprocessNames.join(', ')}` : '',
        getProcessStepDurationLabel(step),
        step.temperature,
        step.expectedOutput ? `Expected: ${step.expectedOutput}` : '',
      ].filter(Boolean)

      return {
        id: node.id,
        title: normalized.title,
        description: descriptionParts.join(' · ') || undefined,
        imageUrl: normalized.visualization?.imageUrl,
        stepNumber: index + 1,
      }
    })
  }, [nodes, availableSubprocesses])

  const applyStepVisualization = useCallback((stepResult: ProcessVisualizationStepResult) => {
    if (!stepResult.success || stepResult.visualizationAssetId == null) return
    setNodes((nds) => nds.map((node) => {
      if (node.id !== stepResult.stepId || !isProcessStepNode(node)) return node
      return {
        ...node,
        data: withProcessStepVisualization(node.data, {
          assetId: stepResult.visualizationAssetId,
          imageUrl: stepResult.imageUrl ?? undefined,
          status: 'generated',
        }),
      }
    }))
  }, [setNodes])

  /**
   * Starts the async per-step visualization job for this process's own STEP nodes (one generated
   * image per step — CONDITION nodes and subprocesses referenced from Action On are never
   * visualized by this call, see ProcessVisualizationService) and polls it until it reaches a
   * terminal status, applying each step's result to the canvas as soon as it shows up in a poll
   * response. Generated images flow into the shared recipe session automatically, the same way any
   * other canvas edit does (see the nodes/edges push-to-session effect above) — no separate
   * plumbing needed here.
   */
  const generateVisuals = useCallback(async () => {
    const stepNodeCount = nodes.filter(isProcessStepNode).length
    if (stepNodeCount === 0) {
      setGenerateVisualsStatus({ type: 'error', text: 'No steps to visualize' })
      return
    }

    setGeneratingVisuals(true)
    setGenerateVisualsStatus(null)

    const appliedStepIds = new Set<string>()
    const applyNewSteps = (steps: ProcessVisualizationStepResult[]) => {
      for (const step of steps) {
        if (!appliedStepIds.has(step.stepId)) {
          applyStepVisualization(step)
          appliedStepIds.add(step.stepId)
        }
      }
    }

    try {
      let job = await ProcessVisualizationApi.startJob(recipeId, processId)

      while (!unmountedRef.current && (job.status === 'QUEUED' || job.status === 'IN_PROGRESS')) {
        await sleep(VISUALIZATION_JOB_POLL_MS)
        if (unmountedRef.current) break

        job = await ProcessVisualizationApi.getJobStatus(recipeId, processId, job.jobId)
        applyNewSteps(job.steps)
      }

      if (unmountedRef.current) return

      const successCount = job.steps.filter((step) => step.success).length
      const failedCount = job.totalSteps - successCount

      setGenerateVisualsStatus(
        job.status === 'COMPLETED'
          ? { type: 'success', text: `Generated visuals for all ${job.totalSteps} steps` }
          : job.status === 'COMPLETED_WITH_ERRORS'
            ? { type: 'error', text: `Generated ${successCount}/${job.totalSteps} steps — ${failedCount} failed` }
            : { type: 'error', text: 'Unable to generate visuals right now.' }
      )
    } catch (error) {
      if (!unmountedRef.current) {
        setGenerateVisualsStatus({
          type: 'error',
          text: error instanceof Error ? error.message : 'Unable to generate visuals right now.',
        })
      }
    } finally {
      if (!unmountedRef.current) setGeneratingVisuals(false)
    }
  }, [recipeId, processId, nodes, applyStepVisualization])

  /** Guards any navigation away from this process (Back button, breadcrumb clicks, opening a subprocess) behind an unsaved-changes confirmation when the *recipe* (any process) has unsaved edits — navigating within the Recipe Tool never discards them either way, but a confirmation still matters when actually leaving via onBack. */
  const confirmNavigatingAway = useCallback(() => !session.anyDirty || window.confirm('You have unsaved changes. Leave without saving?'), [session])

  /** Called from ProcessStepPanel's "Open" button on a referenced subprocess — the only way to open a subprocess from inside a process's own canvas, now that there's no PROCESS node to double-click. */
  const handleOpenSubprocess = useCallback((subprocessId: number) => {
    onOpenSubprocess(subprocessId, process.name)
  }, [process, onOpenSubprocess])

  const handleSave = useCallback(async () => {
    try {
      await session.saveAll()
      notifySuccess('Recipe saved')
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to save this recipe right now')
    }
  }, [session, notifySuccess, notifyError])

  const handleBack = useCallback(() => {
    if (confirmNavigatingAway()) onBack?.()
  }, [confirmNavigatingAway, onBack])

  const handleNavigateToList = useCallback(() => {
    onNavigateToList()
  }, [onNavigateToList])

  const handleNavigateToAncestor = useCallback((index: number) => {
    onNavigateToAncestor(index)
  }, [onNavigateToAncestor])

  const selectedNode = nodes.find((node) => String(node.id) === String(selectedNodeId)) ?? null

  // STEP nodes only, in the same array order the rest of this component already numbers/lists
  // them in (buildSlideshowSteps) — CONDITION nodes are deliberately excluded, both from the step
  // badge numbering (via ProcessGraphContext's stepOrder) and from this top-bar selector.
  const stepNodes = nodes.filter(isProcessStepNode)
  const stepOrder = stepNodes.map((node) => node.id)
  const stepOptions = stepNodes.map((node, index) => ({
    id: node.id,
    label: `${index + 1}. ${normalizeProcessStepNodeData(node.data).title || 'Untitled step'}`,
  }))
  const currentStepId = selectedNode && isProcessStepNode(selectedNode) ? selectedNode.id : undefined

  /**
   * Selects a STEP node from the top bar's step selector and opens the properties panel on it —
   * deliberately does NOT move the viewport (no pan/zoom/fitView): the user's current view of the
   * canvas is left exactly as it was, only the selection changes. Must still flip each node's own
   * `selected` flag via `setNodes` — React Flow tracks selection on the node objects themselves,
   * not just via `selectedNodeId`; setting only the latter left the canvas's own selection model
   * out of sync, so the very next `onSelectionChange` (React Flow's own, e.g. from an unrelated
   * re-render) could silently revert the selector back to nothing.
   */
  const handleSelectStep = useCallback((stepId: string) => {
    setSelectedNodeId(stepId)
    setSelectedEdgeId(null)
    setNodes((nds) => nds.map((node) => (node.selected === (node.id === stepId) ? node : { ...node, selected: node.id === stepId })))
  }, [setNodes])

  return (
    <div className="flow-canvas-container flex h-full w-full flex-col">
      <ProcessTopBar
        name={process.name}
        processType={process.type}
        breadcrumbAncestors={breadcrumbAncestors}
        onNavigateToList={handleNavigateToList}
        onNavigateToAncestor={handleNavigateToAncestor}
        onBack={onBack ? handleBack : undefined}
        onSave={() => void handleSave()}
        isSaving={session.saving}
        isDirty={session.anyDirty}
        saveError={session.saveError}
        onUndo={undo}
        onRedo={redo}
        canUndo={history.length > 0}
        canRedo={future.length > 0}
        onDeleteSelected={(selectedNodeId || selectedEdgeId) ? deleteSelected : undefined}
        zoomPercent={zoomPercent}
        onZoomIn={handleZoomIn}
        onZoomOut={handleZoomOut}
        onFitView={handleFitView}
        onVisualize={() => setShowSlideshow(true)}
        onExport={handleExport}
        onGenerateVisuals={() => void generateVisuals()}
        isGeneratingVisuals={generatingVisuals}
        generateVisualsStatus={generateVisualsStatus}
        onAddStep={() => addNode(FLOW_NODE_TYPES.processStep)}
        onAddCondition={() => addNode(FLOW_NODE_TYPES.condition)}
        steps={stepOptions}
        currentStepId={currentStepId}
        onSelectStep={handleSelectStep}
      />

      <div className="flow-canvas-body" ref={bodyRef}>
        {sidebarHeader && (
          <div
            ref={sidebarRef}
            className={`flow-sidebar-wrapper ${sidebarCollapsed ? 'collapsed' : ''}`}
            style={{ width: sidebarCollapsed ? 48 : sidebarWidth, minWidth: sidebarCollapsed ? 48 : 200 }}
          >
            {sidebarCollapsed ? (
              <div className="sidebar-collapse-tab" role="button" aria-label="Open sidebar" onClick={() => setSidebarCollapsed(false)}>
                ☰
              </div>
            ) : (
              <>
                <div className="sidebar-collapse-button" role="button" title="Collapse sidebar" onClick={() => setSidebarCollapsed(true)} style={{ alignSelf: 'flex-end' }}>◀</div>
                <div style={{ flex: 1, minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                  {sidebarHeader}
                </div>
              </>
            )}
          </div>
        )}

        {sidebarHeader && !sidebarCollapsed && (
          <div
            className="flow-inline-resizer"
            role="separator"
            aria-orientation="vertical"
            aria-label="Resize sidebar"
            onMouseDown={(event) => {
              const startX = event.clientX
              const startWidth = sidebarRef.current?.offsetWidth ?? sidebarWidth
              const minWidth = 200
              const maxWidth = 460

              const onMove = (moveEvent: MouseEvent) => {
                const delta = moveEvent.clientX - startX
                let nextWidth = startWidth + delta
                const propsSpace = propsCollapsed ? 48 : propsWidth
                const gaps = 10 /* sidebar resizer */ + (propsCollapsed ? 0 : 10) /* props resizer */
                const effectiveMax = getMaxPanelWidth(maxWidth, propsSpace, gaps)
                if (nextWidth < minWidth) nextWidth = minWidth
                if (nextWidth > effectiveMax) nextWidth = Math.max(minWidth, effectiveMax)
                setSidebarWidth(nextWidth)
              }

              const onUp = () => {
                document.removeEventListener('mousemove', onMove)
                document.removeEventListener('mouseup', onUp)
              }

              document.addEventListener('mousemove', onMove)
              document.addEventListener('mouseup', onUp)
              event.preventDefault()
            }}
          />
        )}

        <div className="flow-canvas-main">
          <div className="flow-canvas-area">
            <div ref={reactFlowWrapperRef} className="flow-canvas-viewport">
              {/* STEP nodes need subprocess names to show Action On info directly on the canvas (per
                  the brief) — provided via context rather than extra node props, since React Flow's
                  custom node components only receive their own node's data. Ingredient names come
                  from the static catalog module directly, no context needed for those. */}
              <ProcessGraphProvider value={{ availableSubprocesses, stepOrder, onNodeResizeStart: handleNodeResizeStart, onNodeResizeEnd: handleNodeResizeEnd }}>
                <ReactFlow
                  nodes={nodes}
                  edges={edges}
                  onNodesChange={handleNodesChange}
                  onEdgesChange={handleEdgesChange}
                  onConnect={onConnect}
                  isValidConnection={isValidConnection}
                  onNodeDragStart={handleNodeDragStart}
                  onNodeDragStop={handleNodeDragStop}
                  onSelectionChange={({ nodes: selectedNodes, edges: selectedEdges }) => {
                    if (selectedNodes.length > 0) {
                      setSelectedNodeId(selectedNodes[0].id)
                      setSelectedEdgeId(null)
                    } else if (selectedEdges.length > 0) {
                      setSelectedEdgeId(selectedEdges[0].id)
                      setSelectedNodeId(null)
                    } else {
                      setSelectedNodeId(null)
                      setSelectedEdgeId(null)
                    }
                  }}
                  // Disabled so keyboard deletion has exactly one path (our own window keydown
                  // handler above, covering both nodes and edges) instead of two competing ones —
                  // React Flow's own default ('Backspace' only) would otherwise also remove whatever
                  // it thinks is selected via its own onNodesChange/onEdgesChange 'remove' events,
                  // independently of and inconsistently with our selectedNodeId/selectedEdgeId state.
                  deleteKeyCode={null}
                  nodeTypes={nodeTypes}
                  onInit={(instance) => { reactFlowInstance.current = instance }}
                  onMove={handleViewportMove}
                  onMoveEnd={handleViewportMoveEnd}
                  // Only takes effect at this component's own mount — fine here, since this whole
                  // component remounts fresh per process (see the outer ProcessCanvas's `key`).
                  defaultViewport={initialFlowData.viewport ?? undefined}
                  fitView={!initialFlowData.viewport}
                  fitViewOptions={{ padding: 0.12 }}
                  panOnScroll
                  panOnScrollMode={PanOnScrollMode.Free}
                  zoomOnScroll={false}
                  zoomOnPinch
                  selectionOnDrag
                  connectionRadius={28}
                  style={{ width: '100%', height: '100%' }}
                >
                  <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
                  <Controls style={{ borderRadius: 10, border: '1px solid var(--flow-border)', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }} />
                  <MiniMap pannable zoomable style={{ borderRadius: 12, border: '1px solid var(--flow-border)', boxShadow: '0 2px 8px rgba(0,0,0,0.06)' }} />
                </ReactFlow>
              </ProcessGraphProvider>
            </div>
          </div>
        </div>

        <div
          className="flow-inline-resizer"
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize properties panel"
          onMouseDown={(event) => {
            const startX = event.clientX
            const startWidth = propsRef.current?.offsetWidth ?? propsWidth
            const minWidth = 220
            const maxWidth = 520

            const onMove = (moveEvent: MouseEvent) => {
              // Panel is on the right: dragging left (clientX decreases) should grow it.
              const delta = startX - moveEvent.clientX
              let nextWidth = startWidth + delta
              const sidebarSpace = sidebarHeader ? (sidebarCollapsed ? 48 : sidebarWidth) : 0
              const gaps = (sidebarHeader ? 10 : 0) /* sidebar resizer */ + 10 /* this resizer */
              const effectiveMax = getMaxPanelWidth(maxWidth, sidebarSpace, gaps)
              if (nextWidth < minWidth) nextWidth = minWidth
              if (nextWidth > effectiveMax) nextWidth = Math.max(minWidth, effectiveMax)
              setPropsWidth(nextWidth)
            }

            const onUp = () => {
              document.removeEventListener('mousemove', onMove)
              document.removeEventListener('mouseup', onUp)
            }

            document.addEventListener('mousemove', onMove)
            document.addEventListener('mouseup', onUp)
            event.preventDefault()
          }}
        />

        <div
          ref={propsRef}
          className={`flow-properties-wrapper ${propsCollapsed ? 'collapsed' : ''}`}
          style={{ width: propsCollapsed ? 48 : propsWidth, minWidth: propsCollapsed ? 48 : 200 }}
        >
          {propsCollapsed ? (
            <div className="props-collapse-tab" role="button" aria-label="Open properties" onClick={() => setPropsCollapsed(false)}>
              ▶
            </div>
          ) : (
            <>
              <div className="props-collapse-button" role="button" title="Collapse properties" onClick={() => setPropsCollapsed(true)}>▶</div>
              {selectedNode && isProcessStepNode(selectedNode) ? (
                <ProcessStepPanel
                  node={{ id: selectedNode.id, data: selectedNode.data }}
                  availableSubprocesses={availableSubprocesses}
                  updateStepField={updateProcessStepField}
                  updateActionOnIngredients={updateActionOnIngredients}
                  updateActionOnProcesses={updateActionOnProcesses}
                  onDeleteNode={deleteNode}
                  onDuplicateNode={duplicateNode}
                  onOpenSubprocess={handleOpenSubprocess}
                  onGenerateVisuals={() => void generateVisuals()}
                  isGeneratingVisuals={generatingVisuals}
                />
              ) : (
                <PropertiesPanel
                  node={
                    selectedNode && (isAnyStepNode(selectedNode) || isConditionNode(selectedNode))
                      ? { id: selectedNode.id, type: selectedNode.type, data: selectedNode.data as never }
                      : undefined
                  }
                  updateNodeField={updateNodeField}
                  onDeleteNode={deleteNode}
                  onDuplicateNode={duplicateNode}
                />
              )}
            </>
          )}
        </div>
      </div>

      {showSlideshow && (
        <RecipeVisualizationSlideshow steps={buildSlideshowSteps()} onClose={() => setShowSlideshow(false)} />
      )}

      {exportJson && (
        <div className="flow-canvas-export-modal-overlay" onClick={() => setExportJson(null)}>
          <div className="flow-canvas-export-modal" onClick={(e) => e.stopPropagation()}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '14px 18px', borderBottom: '1px solid var(--flow-border)' }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--flow-text)' }}>📤 Export Process</div>
                <div style={{ fontSize: 11, color: 'var(--flow-text-subtle)', marginTop: 2 }}>{nodes.length} nodes · {edges.length} edges</div>
              </div>
              <div style={{ display: 'flex', gap: 7 }}>
                <button onClick={downloadExportJson} style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-success-border)', background: 'var(--flow-success-soft)', color: 'var(--flow-success)', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                  ⬇ Download .json
                </button>
                <button onClick={copyExportJson} style={{ padding: '6px 12px', borderRadius: 7, border: '1px solid var(--flow-info-border)', background: 'var(--flow-info-soft)', color: 'var(--flow-info)', fontSize: 12, fontWeight: 600, cursor: 'pointer' }}>
                  📋 Copy
                </button>
                <button onClick={() => setExportJson(null)} style={{ width: 30, height: 30, borderRadius: 7, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-subtle)', cursor: 'pointer', fontSize: 15 }}>✕</button>
              </div>
            </div>
            <pre style={{ flex: 1, overflow: 'auto', margin: 0, padding: '14px 18px', fontSize: 11, lineHeight: 1.65, color: 'var(--flow-text)', background: 'var(--flow-surface-muted)', fontFamily: "'Fira Code', 'Cascadia Code', monospace" }}>
              {exportJson}
            </pre>
          </div>
        </div>
      )}
    </div>
  )
}
