import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
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

import { recipeNodeComponents } from '../nodes/recipeNodeComponents'
import {
  RECIPE_NODE_TYPES,
  isRecipeConditionNode,
  isRecipeStepNode,
  type RecipeNodeType,
} from '../model/recipeNodeTypes'
import {
  createConditionNode,
  createFlowDataPayload,
  getFlowEdgePresentation,
  normalizeFlowEdges,
  normalizeFlowNode,
  applyConditionFieldUpdate,
} from './recipeProcessCanvas.helpers'
import { normalizeConditionNodeData } from '../model/recipeConditionData'
import { type FlowViewport } from '../model/canvasGraph'
import { buildStepOutputGraph, getStepOutputLabel } from '../model/recipeStepOutputs'
import {
  buildProcessUpdateRequest,
  createRecipeStepNode,
  processToFlowData,
} from '../adapters/recipeProcessCanvasAdapter'
import {
  applyRecipeStepFieldUpdate,
  getActionOnIngredientDisplayName,
  getRecipeStepDurationLabel,
  getRecipeStepTemperatureLabel,
  normalizeRecipeStepNodeData,
  withRecipeStepActionOnIngredients,
  withRecipeStepActionOnProcesses,
  withRecipeStepActionOnSteps,
  withRecipeStepVisualization,
  type ActionOnIngredient,
} from '../model/recipeStepData'
import { RecipeProcessGraphProvider } from '../context/RecipeProcessGraphContext'
import { useRecipeSession, type RecipeSessionContextValue } from '../../context/RecipeSessionContext'
import type { Process } from '../../../../types/process'
import type { RecipeProcessBreadcrumbEntry, RecipeProcessVisualizationStepResult } from '../../../../types/recipe'
import { useNotifications } from '../../../../shared/components/notifications/NotificationProvider'
import { isTrackedJobActive, visualsJobKey } from '../../context/jobTracker'
import { useJobTracker, useTrackedJob } from '../../context/useJobTracker'
import RecipeConditionPanel from './RecipeConditionPanel'
import RecipeStepPanel from './RecipeStepPanel'
import RecipeProcessTopBar from './RecipeProcessTopBar'
import RecipeVisualizationSlideshow, { type SlideshowStep } from './RecipeVisualizationSlideshow'
import '../../styles/recipe-tool.css'
import '../styles/RecipeProcessSidebar.css'
import '../styles/RecipePropertiesPanel.css'
import '../styles/RecipeProcessCanvas.css'

type GenerateVisualsStatus = { type: 'success' | 'error' | 'info'; text: string }

type RecipeProcessCanvasProps = {
  recipeId: number
  processId: number
  /** Ancestors from the recipe's process list down to this process's parent, root-first (empty for MAIN or a directly-opened process). */
  breadcrumbAncestors: RecipeProcessBreadcrumbEntry[]
  onNavigateToList: () => void
  onNavigateToAncestor: (index: number) => void
  /** Called when the user chooses to open a subprocess referenced from a STEP's Action On (see RecipeStepPanel's "Open" button) — a Process graph never contains a node for this, so there's no double-click-to-open path anymore. */
  onOpenSubprocess: (subprocessId: number, currentProcessName: string) => void
  onBack?: () => void
  /**
   * The Recipe Tool's process list (see RecipeEditorView), rendered as this component's whole
   * left column when present — filling it entirely, not split with anything else, now that node
   * add/navigation live in RecipeProcessTopBar and the old Tool Options panel has nothing left to show.
   * Omitted for the standalone `/process/:processId` route, where there's no sibling process list;
   * that route simply has no left column at all (the canvas takes the full width instead).
   */
  sidebarHeader?: ReactNode
  /** Reports the selected node (null when none) — the AI edit dialog resolves "this step" with it. */
  onSelectedNodeChange?: (nodeId: string | null) => void
  /** Nodes to briefly highlight (e.g. the ones an AI edit just changed); display only, never saved. */
  highlightedNodeIds?: ReadonlySet<string>
}

/** JSON of exactly what a save would send for this graph — equal strings mean nothing worth saving changed. */
const graphPayloadJson = (flowNodes: Node[], flowEdges: Edge[]) => {
  const { nodes, edges } = buildProcessUpdateRequest('', undefined, createFlowDataPayload(flowNodes, flowEdges))
  return JSON.stringify([nodes, edges])
}

/**
 * A recipe is one unified editing session — MAIN and every SUBPROCESS are
 * views of the same in-memory snapshot (RecipeSessionContext), not
 * independent flows. This outer component owns only what should genuinely
 * survive a process switch: the resizable panel layout (widths/collapsed
 * state). Everything that is legitimately *per-process* — the canvas's
 * nodes/edges, selection, zoom/viewport — lives in RecipeProcessCanvasContent
 * below, which is deliberately remounted (via `key`) whenever `processId`
 * changes. Undo/redo is recipe-wide and lives in the session itself, so it
 * survives process switches and remounts.
 *
 * This is *not* the same kind of remount the Recipe Tool used to do: that
 * remounted this whole component (topbar, panels, layout *and* data) and
 * re-fetched the process from the backend, which is what lost unsaved edits
 * and made switching feel like opening a separate flow. Here, the process's
 * data was already loaded once into RecipeSessionContext (no fetch on
 * switch), every edit is continuously pushed into that shared session as it
 * happens (not only on unmount), and only the small, genuinely per-process
 * slice of local UI state (selection, zoom) resets. Switching back to a
 * previously-edited process re-seeds straight from the session, so its
 * unsaved changes are still there.
 */
export default function RecipeProcessCanvas(props: RecipeProcessCanvasProps) {
  const session = useRecipeSession()
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [sidebarWidth, setSidebarWidth] = useState(260)
  const [propsCollapsed, setPropsCollapsed] = useState(true)
  const [propsWidth, setPropsWidth] = useState(280)

  if (!session || session.loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading process…
      </div>
    )
  }

  // A save may have just given a pending (temporary-id) process its real id; follow it, so the open
  // canvas keeps showing (and writing to) the same process until the selection catches up.
  const processId = session.getProcess(props.processId) ? props.processId : session.resolveProcessId(props.processId)
  const process = session.getProcess(processId)

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

  const availableSubprocesses = session.getProcesses().filter((candidate) => candidate.type === 'SUBPROCESS' && candidate.id !== processId)

  return (
    <RecipeProcessCanvasContent
      key={processId}
      {...props}
      processId={processId}
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
    />
  )
}

type RecipeProcessCanvasContentProps = RecipeProcessCanvasProps & {
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
}

function RecipeProcessCanvasContent({
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
  onSelectedNodeChange,
  highlightedNodeIds,
  sidebarCollapsed,
  setSidebarCollapsed,
  sidebarWidth,
  setSidebarWidth,
  propsCollapsed,
  setPropsCollapsed,
  propsWidth,
  setPropsWidth,
}: RecipeProcessCanvasContentProps) {
  const { notifySuccess, notifyError } = useNotifications()

  // Seeded once, synchronously, from the already-loaded session — no fetch, no loading flash.
  // Recomputed on every render of this instance, but only the *first* render's value is actually
  // used (React ignores later arguments to useNodesState/useEdgesState) since this whole component
  // remounts fresh (via `key={processId}` in RecipeProcessCanvas above) whenever the selected process
  // changes, rather than resetting this state in place.
  const initialFlowData = processToFlowData(process)
  const [nodes, setNodes, onNodesChange] = useNodesState(initialFlowData.nodes.map(normalizeFlowNode))
  const [edges, setEdges, onEdgesChange] = useEdgesState(normalizeFlowEdges(initialFlowData.edges))
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [zoomPercent, setZoomPercent] = useState(Math.round((initialFlowData.viewport?.zoom ?? 1) * 100))
  const [exportJson, setExportJson] = useState<string | null>(null)
  const [showSlideshow, setShowSlideshow] = useState(false)
  // Narration is generated from the *saved* step text, so opening the slideshow first flushes any
  // pending autosave; the slideshow waits on this before asking the backend for narration.
  const [narrationReady, setNarrationReady] = useState<Promise<boolean> | null>(null)
  const openSlideshow = useCallback(() => {
    setNarrationReady(session.saveNow().then((result) => result.ok, () => false))
    setShowSlideshow(true)
  }, [session])
  // Generate Visuals state lives in the app-level JobTracker (keyed by process), so it's still
  // correct after this instance remounts or the page reloads; only the start request is local.
  const jobTracker = useJobTracker()
  const visualsJob = useTrackedJob(visualsJobKey(processId))
  const [startingVisuals, setStartingVisuals] = useState(false)
  const generatingVisuals = startingVisuals || isTrackedJobActive(visualsJob)
  const [generateVisualsMessage, setGenerateVisualsMessage] = useState<GenerateVisualsStatus | null>(null)

  // Guards generateVisuals below against setState-after-unmount — this instance remounts fresh
  // per process (key={processId} on the outer RecipeProcessCanvas), so a start request still in
  // flight when switching processes must not touch this instance's state once it's gone. Reset on every
  // (re-)mount rather than declared once: React StrictMode's
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
  // its own static max.
  const MIN_CANVAS_WIDTH = 280
  const getMaxPanelWidth = useCallback((staticMax: number, otherPanelWidth: number, gaps: number) => {
    const containerWidth = bodyRef.current?.offsetWidth ?? window.innerWidth
    const dynamicMax = containerWidth - otherPanelWidth - gaps - MIN_CANVAS_WIDTH
    return Math.min(staticMax, dynamicMax)
  }, [])

  useEffect(() => {
    onSelectedNodeChange?.(selectedNodeId)
  }, [selectedNodeId, onSelectedNodeChange])

  // Highlighting is applied only to what React Flow renders, so it never reaches the session or a save.
  const displayedNodes = useMemo(() => (highlightedNodeIds && highlightedNodeIds.size > 0
    ? nodes.map((node) => (highlightedNodeIds.has(node.id)
      ? { ...node, className: [node.className, 'flow-node-ai-changed'].filter(Boolean).join(' ') }
      : node))
    : nodes), [nodes, highlightedNodeIds])

  // Auto-collapse the properties panel when nothing is selected, expand it when a node is selected,
  // so the rail only takes up space while it has something to show.
  useEffect(() => {
    setPropsCollapsed(selectedNodeId == null)
  }, [selectedNodeId, setPropsCollapsed])

  // Publishes this process's nodes/edges into the shared recipe session on every *real* change —
  // not just on Save — so switching to a different process (or the Recipe Process view) always sees
  // this process's latest edits, and autosave persists them. "Real" is decided on exactly what a save
  // would send (the payload's JSON), so selection, re-measuring the same sizes, or re-seeding never
  // marks the recipe unsaved — robust under StrictMode's double-invoked effects too, unlike a
  // "skip the first run" flag. Not pushed mid-gesture (each drag/resize tick): the gesture's end
  // pushes once (see the drag/resize handlers below).
  const [seededGraphJson] = useState(() => graphPayloadJson(nodes, edges))
  const syncedGraphJsonRef = useRef(seededGraphJson)
  const resizeSnapshotRef = useRef<{ nodeId: string; before: Process[]; width?: number; height?: number } | null>(null)
  const pushToSession = useCallback((flowNodes: Node[], flowEdges: Edge[]) => {
    const json = graphPayloadJson(flowNodes, flowEdges)
    if (json === syncedGraphJsonRef.current) return
    syncedGraphJsonRef.current = json
    const flowData = createFlowDataPayload(flowNodes, flowEdges, currentViewportRef.current ?? undefined)
    const { nodes: processNodes, edges: processEdges } = buildProcessUpdateRequest(process.name, process.description, flowData)
    session.updateProcess(processId, { nodes: processNodes ?? [], edges: processEdges ?? [] })
  }, [session, processId, process.name, process.description])
  useEffect(() => {
    if (resizeSnapshotRef.current || nodes.some((node) => node.dragging)) return
    pushToSession(nodes, edges)
  }, [nodes, edges, pushToSession])

  // Re-seeds the canvas in place when the session's content changed from outside it — undo/redo,
  // recovering local changes, AI generation replacing this process, or a save assigning real ids
  // to processes this one references. Keeps the current selection where the node still exists.
  const { subscribeReseed, getProcess } = session
  useEffect(() => subscribeReseed(() => {
    const latest = getProcess(processId)
    if (!latest) return
    const flowData = processToFlowData(latest)
    const nextNodes = flowData.nodes.map(normalizeFlowNode)
    const nextEdges = normalizeFlowEdges(flowData.edges)
    syncedGraphJsonRef.current = graphPayloadJson(nextNodes, nextEdges)
    setNodes((current) => {
      const selected = new Set(current.filter((node) => node.selected).map((node) => node.id))
      return nextNodes.map((node) => (selected.has(node.id) ? { ...node, selected: true } : node))
    })
    setEdges(nextEdges)
    setSelectedNodeId((current) => (current != null && nextNodes.some((node) => node.id === current) ? current : null))
    setSelectedEdgeId((current) => (current != null && nextEdges.some((edge) => edge.id === current) ? current : null))
  }), [subscribeReseed, getProcess, processId, setNodes, setEdges])

  /** Records an undo step for a structural change (add/delete/duplicate node, connect/remove edge) — call before applying it. */
  const pushHistorySnapshot = useCallback(() => {
    session.recordHistory({ focusProcessId: processId })
  }, [session, processId])

  /** Field edits: one undo step per typing burst in one field, not one per keystroke. */
  const recordFieldEdit = useCallback((nodeId: string, field: string) => {
    session.recordHistory({ focusProcessId: processId, coalesceKey: `${processId}:${nodeId}:${field}` })
  }, [session, processId])

  const undo = session.undo
  const redo = session.redo

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
   * which fire continuously with `dragging: true` while the mouse moves). The session snapshot from
   * the gesture's start becomes the undo step, and only if the node actually ended up elsewhere.
   */
  const dragSnapshotRef = useRef<{ nodeId: string; before: Process[]; x: number; y: number } | null>(null)
  const handleNodeDragStart = useCallback((_event: unknown, node: Node) => {
    dragSnapshotRef.current = { nodeId: node.id, before: session.getProcesses(), x: node.position.x, y: node.position.y }
  }, [session])
  const handleNodeDragStop = useCallback((_event: unknown, node: Node) => {
    const snapshot = dragSnapshotRef.current
    dragSnapshotRef.current = null
    if (!snapshot || snapshot.nodeId !== node.id) return
    if (snapshot.x === node.position.x && snapshot.y === node.position.y) return
    session.recordHistory({ before: snapshot.before, focusProcessId: processId })
  }, [session, processId])

  /**
   * Same one-entry-per-gesture treatment for resize, driven by RecipeProcessGraphContext's
   * onNodeResizeStart/onNodeResizeEnd (NodeResizeControl's own start/end callbacks live inside each
   * node component, not here — see RecipeStepNode.tsx/RecipeConditionNode.tsx) rather than the
   * `dimensions` NodeChange stream, which fires on every intermediate resize tick. Session pushes
   * are paused in between, so the end pushes the final size once.
   */
  const handleNodeResizeStart = useCallback((nodeId: string) => {
    const node = nodesRef.current.find((n) => n.id === nodeId)
    resizeSnapshotRef.current = {
      nodeId,
      before: session.getProcesses(),
      width: node?.width ?? node?.measured?.width,
      height: node?.height ?? node?.measured?.height,
    }
  }, [session])
  const handleNodeResizeEnd = useCallback((nodeId: string) => {
    const snapshot = resizeSnapshotRef.current
    resizeSnapshotRef.current = null
    if (!snapshot || snapshot.nodeId !== nodeId) return
    const after = nodesRef.current.find((n) => n.id === nodeId)
    const resized = snapshot.width !== (after?.width ?? after?.measured?.width) || snapshot.height !== (after?.height ?? after?.measured?.height)
    if (resized) session.recordHistory({ before: snapshot.before, focusProcessId: processId })
    pushToSession(nodesRef.current, edgesRef.current)
  }, [session, processId, pushToSession])

  const addNode = useCallback((nodeType: RecipeNodeType) => {
    pushHistorySnapshot()
    const id = crypto.randomUUID()
    const position = { x: 420 + (nodes.length % 4) * 40, y: 140 + nodes.length * 50 }
    const newNode =
      nodeType === RECIPE_NODE_TYPES.condition
        ? createConditionNode(id, position)
        : createRecipeStepNode(id, position)

    setNodes((nds) => [...nds, newNode])
    setSelectedNodeId(id)
    setSelectedEdgeId(null)
  }, [nodes.length, setNodes, pushHistorySnapshot])

  const deleteNode = useCallback((id: string) => {
    pushHistorySnapshot()
    // Also drops other steps' step-output references to the deleted node, which would otherwise
    // point at nothing (and fail backend validation on save).
    setNodes((nds) => nds
      .filter((node) => node.id !== id)
      .map((node) => {
        if (!isRecipeStepNode(node)) return node
        const steps = normalizeRecipeStepNodeData(node.data).step.actionOn.steps
        if (!steps.some((entry) => entry.stepId === id)) return node
        return { ...node, data: withRecipeStepActionOnSteps(node.data, steps.map((entry) => entry.stepId).filter((stepId) => stepId !== id)) }
      }))
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

  // Keyboard shortcuts (Delete/Backspace, Ctrl/Cmd+Z, Ctrl/Cmd+Y) — a global listener so shortcuts
  // work regardless of which element inside the canvas currently has focus,
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
    recordFieldEdit(nodeId, field)
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? applyConditionFieldUpdate(node, field, value) : node)))

    if (field === 'condition.successLabel' || field === 'condition.failureLabel') {
      const sourceHandle = field === 'condition.successLabel' ? 'condition-yes' : 'condition-no'
      const fallbackLabel = field === 'condition.successLabel' ? 'Yes' : 'No'
      setEdges((eds) => eds.map((edge) =>
        edge.source === nodeId && edge.sourceHandle === sourceHandle
          ? { ...edge, label: value.trim() || fallbackLabel }
          : edge))
    }
  }, [setNodes, setEdges, recordFieldEdit])

  const updateRecipeStepField = useCallback((nodeId: string, field: string, value: string) => {
    recordFieldEdit(nodeId, field)
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: applyRecipeStepFieldUpdate(node.data, field, value) } : node)))
  }, [setNodes, recordFieldEdit])

  const updateActionOnIngredients = useCallback((nodeId: string, ingredients: ActionOnIngredient[]) => {
    recordFieldEdit(nodeId, 'actionOn.ingredients')
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withRecipeStepActionOnIngredients(node.data, ingredients) } : node)))
  }, [setNodes, recordFieldEdit])

  const updateActionOnProcesses = useCallback((nodeId: string, processIds: number[]) => {
    recordFieldEdit(nodeId, 'actionOn.processes')
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withRecipeStepActionOnProcesses(node.data, processIds) } : node)))
  }, [setNodes, recordFieldEdit])

  const updateActionOnSteps = useCallback((nodeId: string, stepIds: string[]) => {
    recordFieldEdit(nodeId, 'actionOn.steps')
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withRecipeStepActionOnSteps(node.data, stepIds) } : node)))
  }, [setNodes, recordFieldEdit])

  // Which earlier steps' Expected Outputs each step may reference, and their live labels — derived
  // from the current nodes/edges (see recipeStepOutputs.ts), never stored.
  const stepOutputGraph = useMemo(() => buildStepOutputGraph(nodes, edges), [nodes, edges])

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

    if (sourceNode && isRecipeConditionNode(sourceNode)) {
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
    const stepNodes = nodes.filter(isRecipeStepNode)
    return stepNodes.map((node, index) => {
      const normalized = normalizeRecipeStepNodeData(node.data)
      const { step } = normalized
      const ingredientNames = [
        ...step.actionOn.steps.map((entry) => getStepOutputLabel(stepOutputGraph, entry.stepId)).filter(Boolean),
        ...step.actionOn.ingredients.map(getActionOnIngredientDisplayName),
      ]
      const subprocessNames = step.actionOn.processes
        .map((entry) => availableSubprocesses.find((candidate) => candidate.id === entry.processId)?.name)
        .filter((name): name is string => Boolean(name))
      const descriptionParts = [
        step.actionDescription,
        ingredientNames.length > 0 ? `On: ${ingredientNames.join(', ')}` : '',
        subprocessNames.length > 0 ? `Using: ${subprocessNames.join(', ')}` : '',
        getRecipeStepDurationLabel(step),
        getRecipeStepTemperatureLabel(step),
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
  }, [nodes, availableSubprocesses, stepOutputGraph])

  const applyStepVisualization = useCallback((stepResult: RecipeProcessVisualizationStepResult) => {
    if (!stepResult.success || stepResult.visualizationAssetId == null) return
    setNodes((nds) => nds.map((node) => {
      if (node.id !== stepResult.stepId || !isRecipeStepNode(node)) return node
      return {
        ...node,
        data: withRecipeStepVisualization(node.data, {
          assetId: stepResult.visualizationAssetId,
          imageUrl: stepResult.imageUrl ?? undefined,
          status: 'generated',
        }),
      }
    }))
  }, [setNodes])

  /**
   * Starts (or joins the already-running) per-step visualization job for this process's own STEP
   * nodes — one generated image per step; CONDITION nodes and subprocesses referenced from Action
   * On are never visualized by this call, see RecipeProcessVisualizationService. The job itself is
   * followed by the app-level JobTracker, not this component, so it keeps running (and its progress
   * stays visible) across process switches, page navigation and reloads.
   */
  const generateVisuals = useCallback(async () => {
    if (generatingVisuals) return
    const stepNodeCount = nodes.filter(isRecipeStepNode).length
    if (stepNodeCount === 0) {
      setGenerateVisualsMessage({ type: 'error', text: 'No steps to visualize' })
      return
    }

    setGenerateVisualsMessage(null)
    setStartingVisuals(true)
    try {
      await jobTracker.startVisuals(recipeId, processId)
    } catch (error) {
      if (!unmountedRef.current) {
        setGenerateVisualsMessage({
          type: 'error',
          text: error instanceof Error ? error.message : 'Unable to generate visuals right now.',
        })
      }
    } finally {
      if (!unmountedRef.current) setStartingVisuals(false)
    }
  }, [generatingVisuals, nodes, jobTracker, recipeId, processId])

  // Applies each step's result to the canvas as soon as it shows up in a poll — including, on
  // (re)mount, every step a job already finished while this process wasn't open. Generated images
  // then flow into the shared recipe session the same way any other canvas edit does (see the
  // nodes/edges push-to-session effect above).
  const appliedVisualStepsRef = useRef(new Set<string>())
  useEffect(() => {
    if (!visualsJob) return
    for (const step of visualsJob.job.steps) {
      const appliedKey = `${visualsJob.job.jobId}:${step.stepId}`
      if (appliedVisualStepsRef.current.has(appliedKey)) continue
      applyStepVisualization(step)
      appliedVisualStepsRef.current.add(appliedKey)
    }
  }, [visualsJob, applyStepVisualization])

  const generateVisualsStatus = useMemo<GenerateVisualsStatus | null>(() => {
    if (generateVisualsMessage) return generateVisualsMessage
    if (!visualsJob) return null
    const { job } = visualsJob
    if (visualsJob.pollError) return { type: 'error', text: `Lost track of visuals generation: ${visualsJob.pollError}` }
    if (isTrackedJobActive(visualsJob)) {
      const progress = `Generating visuals… ${job.completedSteps}/${job.totalSteps} steps`
      return { type: 'info', text: visualsJob.connectionLost ? `${progress} (connection lost, retrying…)` : progress }
    }
    const successCount = job.steps.filter((step) => step.success).length
    if (job.status === 'COMPLETED') return { type: 'success', text: `Generated visuals for all ${job.totalSteps} steps` }
    if (job.status === 'COMPLETED_WITH_ERRORS') return { type: 'error', text: `Generated ${successCount}/${job.totalSteps} steps — ${job.totalSteps - successCount} failed` }
    return { type: 'error', text: job.errorMessage || 'Unable to generate visuals right now.' }
  }, [generateVisualsMessage, visualsJob])

  const visualsProgressLabel = visualsJob && isTrackedJobActive(visualsJob)
    ? `Generating… ${visualsJob.job.completedSteps}/${visualsJob.job.totalSteps}`
    : undefined

  /**
   * Guards actually leaving via onBack. Pending edits are simply saved on the way out (autosave
   * flush); only when saving is currently failing is there anything to confirm — and even then the
   * edits stay on this device and are offered back when the recipe is reopened.
   */
  const confirmNavigatingAway = useCallback(() => {
    const { status } = session.saveState
    if (status !== 'failed' && status !== 'conflict') return true
    return window.confirm('Your latest changes could not be saved yet. They are kept on this device and will be offered back when you reopen this recipe. Leave anyway?')
  }, [session])

  /** Called from RecipeStepPanel's "Open" button on a referenced subprocess — the only way to open a subprocess from inside a process's own canvas, now that there's no PROCESS node to double-click. */
  const handleOpenSubprocess = useCallback((subprocessId: number) => {
    onOpenSubprocess(subprocessId, process.name)
  }, [process, onOpenSubprocess])

  /** Explicit Save: flushes the pending autosave right away. Autosave itself never toasts. */
  const handleSave = useCallback(async () => {
    const { ok, error } = await session.saveNow()
    if (ok) notifySuccess('Recipe saved')
    else notifyError(error ?? 'Unable to save this recipe right now')
  }, [session, notifySuccess, notifyError])

  const handleResolveConflict = useCallback((choice: 'reload' | 'keepMine') => {
    if (choice === 'reload' && !window.confirm('Discard the changes made here and load the latest saved version of this recipe?')) return
    session.resolveConflict(choice).catch((error) => {
      notifyError(error instanceof Error ? error.message : 'Unable to resolve the conflict right now')
    })
  }, [session, notifyError])

  const handleBack = useCallback(() => {
    if (!confirmNavigatingAway()) return
    void session.saveNow()
    onBack?.()
  }, [confirmNavigatingAway, session, onBack])

  const handleNavigateToList = useCallback(() => {
    onNavigateToList()
  }, [onNavigateToList])

  const handleNavigateToAncestor = useCallback((index: number) => {
    onNavigateToAncestor(index)
  }, [onNavigateToAncestor])

  const selectedNode = nodes.find((node) => String(node.id) === String(selectedNodeId)) ?? null

  // STEP nodes only, in the same array order the rest of this component already numbers/lists
  // them in (buildSlideshowSteps) — CONDITION nodes are deliberately excluded, both from the step
  // badge numbering (via RecipeProcessGraphContext's stepOrder) and from this top-bar selector.
  const stepNodes = nodes.filter(isRecipeStepNode)
  const stepOrder = stepNodes.map((node) => node.id)
  const stepOptions = stepNodes.map((node, index) => ({
    id: node.id,
    label: `${index + 1}. ${normalizeRecipeStepNodeData(node.data).title || 'Untitled step'}`,
  }))
  const currentStepId = selectedNode && isRecipeStepNode(selectedNode) ? selectedNode.id : undefined

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
      <RecipeProcessTopBar
        name={process.name}
        processType={process.type}
        breadcrumbAncestors={breadcrumbAncestors}
        onNavigateToList={handleNavigateToList}
        onNavigateToAncestor={handleNavigateToAncestor}
        onBack={onBack ? handleBack : undefined}
        onSave={() => void handleSave()}
        saveState={session.saveState}
        onResolveConflict={handleResolveConflict}
        onUndo={undo}
        onRedo={redo}
        canUndo={session.canUndo}
        canRedo={session.canRedo}
        onDeleteSelected={(selectedNodeId || selectedEdgeId) ? deleteSelected : undefined}
        zoomPercent={zoomPercent}
        onZoomIn={handleZoomIn}
        onZoomOut={handleZoomOut}
        onFitView={handleFitView}
        onVisualize={openSlideshow}
        onExport={handleExport}
        onGenerateVisuals={() => void generateVisuals()}
        isGeneratingVisuals={generatingVisuals}
        visualsProgressLabel={visualsProgressLabel}
        generateVisualsStatus={generateVisualsStatus}
        onAddStep={() => addNode(RECIPE_NODE_TYPES.step)}
        onAddCondition={() => addNode(RECIPE_NODE_TYPES.condition)}
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
              <RecipeProcessGraphProvider value={{ availableSubprocesses, stepOrder, stepOutputGraph, onNodeResizeStart: handleNodeResizeStart, onNodeResizeEnd: handleNodeResizeEnd }}>
                <ReactFlow
                  nodes={displayedNodes}
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
                  nodeTypes={recipeNodeComponents}
                  onInit={(instance) => { reactFlowInstance.current = instance }}
                  onMove={handleViewportMove}
                  onMoveEnd={handleViewportMoveEnd}
                  // Only takes effect at this component's own mount — fine here, since this whole
                  // component remounts fresh per process (see the outer RecipeProcessCanvas's `key`).
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
              </RecipeProcessGraphProvider>
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
              {selectedNode && isRecipeStepNode(selectedNode) ? (
                <RecipeStepPanel
                  node={{ id: selectedNode.id, data: selectedNode.data }}
                  availableSubprocesses={availableSubprocesses}
                  updateStepField={updateRecipeStepField}
                  updateActionOnIngredients={updateActionOnIngredients}
                  updateActionOnProcesses={updateActionOnProcesses}
                  updateActionOnSteps={updateActionOnSteps}
                  stepOutputGraph={stepOutputGraph}
                  onDeleteNode={deleteNode}
                  onDuplicateNode={duplicateNode}
                  onOpenSubprocess={handleOpenSubprocess}
                  onGenerateVisuals={() => void generateVisuals()}
                  isGeneratingVisuals={generatingVisuals}
                  visualsProgressLabel={visualsProgressLabel}
                />
              ) : (
                <RecipeConditionPanel
                  node={
                    selectedNode && isRecipeConditionNode(selectedNode)
                      ? { id: selectedNode.id, data: selectedNode.data as never }
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
        <RecipeVisualizationSlideshow
          steps={buildSlideshowSteps()}
          recipeId={recipeId}
          processId={processId}
          narrationReady={narrationReady}
          onClose={() => setShowSlideshow(false)}
        />
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
