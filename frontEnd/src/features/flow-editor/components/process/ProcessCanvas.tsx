import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import {
  ReactFlow,
  Background,
  MiniMap,
  useNodesState,
  useEdgesState,
  addEdge,
  BackgroundVariant,
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
  flowNodeToProcessNode,
  processToFlowData,
} from '../../adapters/processFlowAdapter'
import {
  applyProcessStepFieldUpdate,
  getProcessStepDurationLabel,
  normalizeProcessStepNodeData,
  withProcessStepActionOnIngredients,
  withProcessStepActionOnProcesses,
} from '../../model/processStepData'
import { ProcessGraphProvider } from '../../context/ProcessGraphContext'
import { useProcessLiveGraph } from '../../context/ProcessLiveGraphContext'
import { ProcessApi, IngredientCatalogApi, type GlobalIngredient } from '../../../../api'
import type { Process, ProcessBreadcrumbEntry, RecipeIngredient } from '../../../../types/process'
import { useNotifications } from '../../../../shared/components/notifications/NotificationProvider'
import PropertiesPanel from '../toolbar/PropertiesPanel'
import ProcessSidebar from './ProcessSidebar'
import ProcessStepPanel from './ProcessStepPanel'
import ProcessTopBar from './ProcessTopBar'
import RecipeVisualizationSlideshow, { type SlideshowStep } from '../canvas/RecipeVisualizationSlideshow'
import '../../styles/flow-editor.css'
import '../sidebar/Sidebar.css'
import '../toolbar/PropertiesPanel.css'
import '../canvas/FlowCanvas.css'

const initialNodes: Node[] = []
const initialEdges: Edge[] = []

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
   * Content rendered above ProcessSidebar's own "Tool Options" content, inside the same left
   * column, split ~50/50 — the Recipe Tool's process list (see RecipeProcessView), so the two no
   * longer sit in separate side-by-side columns. Omitted for the standalone `/process/:processId`
   * route, where there's no sibling process list and ProcessSidebar fills the whole column.
   */
  sidebarHeader?: ReactNode
}

type ProcessMeta = {
  name: string
  description?: string
  type: Process['type']
}

export default function ProcessCanvas({
  recipeId,
  processId,
  breadcrumbAncestors,
  onNavigateToList,
  onNavigateToAncestor,
  onOpenSubprocess,
  onBack,
  sidebarHeader,
}: ProcessCanvasProps) {
  const { notifySuccess, notifyError } = useNotifications()
  const liveGraph = useProcessLiveGraph()
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges)
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [processMeta, setProcessMeta] = useState<ProcessMeta | null>(null)
  const [recipeProcesses, setRecipeProcesses] = useState<Process[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  // Set once, from the initial load, purely to decide the canvas's defaultViewport/fitView props at
  // first mount. Refs can't be read during render (only in effects/handlers), so this can't just
  // reuse currentViewportRef below, even though the two are set together.
  const [initialViewport, setInitialViewport] = useState<FlowViewport | null>(null)
  const [zoomPercent, setZoomPercent] = useState(100)
  const [ingredientCatalog, setIngredientCatalog] = useState<GlobalIngredient[]>([])
  const [history, setHistory] = useState<{ nodes: Node[]; edges: Edge[] }[]>([])
  const [future, setFuture] = useState<{ nodes: Node[]; edges: Edge[] }[]>([])
  const [exportJson, setExportJson] = useState<string | null>(null)
  const [showSlideshow, setShowSlideshow] = useState(false)

  const reactFlowInstance = useRef<ReactFlowInstance<Node, Edge> | null>(null)
  const currentViewportRef = useRef<FlowViewport | null>(null)

  const markDirty = useCallback(() => setDirty(true), [])

  // The Action On ingredient picker needs the recipe's real global catalog (name/image) — loaded
  // once here, independently of the process load, so this canvas keeps working standalone (e.g. the
  // direct /process/:processId route) and not only when hosted inside the Recipe Tool.
  useEffect(() => {
    IngredientCatalogApi.list()
      .then(setIngredientCatalog)
      .catch(() => setIngredientCatalog([]))
  }, [])

  // Publishes this process's current in-memory STEP/CONDITION nodes to the shared live-graph store
  // (see ProcessLiveGraphContext) on every nodes/edges change — not just on Save — so the Recipe
  // Tool's Ingredients view can derive Action On ingredients from unsaved edits immediately. Gated
  // on `!loading` so a fresh mount's still-empty initial nodes don't briefly overwrite this
  // process's last-known (possibly non-empty) live entry before the real data has loaded.
  useEffect(() => {
    if (!liveGraph || loading) return
    const flowData = createFlowDataPayload(nodes, edges)
    liveGraph.setLiveNodes(processId, flowData.nodes.map(flowNodeToProcessNode))
  }, [liveGraph, loading, processId, nodes, edges])

  /** Snapshots nodes/edges *before* a mutation, for Undo. Deliberately not called on every field
   * keystroke (only structural changes: add/delete/duplicate node, connect/remove edge, drag end) —
   * snapshotting per-keystroke would flood the stack without much undo value. */
  const pushHistorySnapshot = useCallback(() => {
    setHistory((h) => [...h, { nodes: structuredClone(nodes), edges: structuredClone(edges) }].slice(-50))
    setFuture([])
  }, [nodes, edges])

  const undo = useCallback(() => {
    if (history.length === 0) return
    const previous = history[history.length - 1]
    setFuture((f) => [{ nodes: structuredClone(nodes), edges: structuredClone(edges) }, ...f])
    setNodes(previous.nodes)
    setEdges(previous.edges)
    setHistory((h) => h.slice(0, -1))
    markDirty()
  }, [history, nodes, edges, setNodes, setEdges, markDirty])

  const redo = useCallback(() => {
    if (future.length === 0) return
    const next = future[0]
    setHistory((h) => [...h, { nodes: structuredClone(nodes), edges: structuredClone(edges) }])
    setNodes(next.nodes)
    setEdges(next.edges)
    setFuture((f) => f.slice(1))
    markDirty()
  }, [future, nodes, edges, setNodes, setEdges, markDirty])

  useEffect(() => {
    let cancelled = false

    // Note: `loading`/`loadError` are only ever updated inside these async callbacks, never
    // synchronously here (the effect body itself does no setState). That's fine because the route
    // wrapper (App.tsx's ProcessEditorRoute) keys this component by recipeId+processId, so opening a
    // different process — including via subprocess navigation — always remounts this component with
    // fresh initial state rather than re-running this effect in place.
    Promise.all([ProcessApi.get(recipeId, processId), ProcessApi.listByRecipe(recipeId)])
      .then(([process, allProcesses]) => {
        if (cancelled) return
        const flowData = processToFlowData(process)

        setNodes(flowData.nodes.map(normalizeFlowNode))
        setEdges(normalizeFlowEdges(flowData.edges))
        setRecipeProcesses(allProcesses)
        setProcessMeta({ name: process.name, description: process.description, type: process.type })
        currentViewportRef.current = flowData.viewport ?? null
        setInitialViewport(flowData.viewport ?? null)
        setZoomPercent(Math.round((flowData.viewport?.zoom ?? 1) * 100))
        setHistory([])
        setFuture([])
        setDirty(false)
        setLoadError(null)
      })
      .catch((error) => {
        if (!cancelled) {
          setLoadError(error instanceof Error ? error.message : 'Unable to load this process')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recipeId, processId])

  const handleNodesChange = useCallback((changes: NodeChange<Node>[]) => {
    const isStructural = changes.some((change) =>
      change.type === 'remove' || change.type === 'dimensions' || (change.type === 'position' && change.dragging === false))
    if (isStructural) pushHistorySnapshot()
    if (changes.some((change) => change.type === 'position' || change.type === 'remove' || change.type === 'dimensions')) {
      markDirty()
    }
    onNodesChange(changes)
  }, [onNodesChange, markDirty, pushHistorySnapshot])

  const handleEdgesChange = useCallback((changes: EdgeChange<Edge>[]) => {
    if (changes.some((change) => change.type === 'remove')) pushHistorySnapshot()
    if (changes.some((change) => change.type === 'remove' || change.type === 'add')) {
      markDirty()
    }
    onEdgesChange(changes)
  }, [onEdgesChange, markDirty, pushHistorySnapshot])

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
    markDirty()
  }, [nodes.length, setNodes, markDirty, pushHistorySnapshot])

  const deleteNode = useCallback((id: string) => {
    pushHistorySnapshot()
    setNodes((nds) => nds.filter((node) => node.id !== id))
    setEdges((eds) => eds.filter((edge) => edge.source !== id && edge.target !== id))
    setSelectedNodeId((current) => (current === id ? null : current))
    markDirty()
  }, [setNodes, setEdges, markDirty, pushHistorySnapshot])

  const deleteSelected = useCallback(() => {
    if (selectedNodeId) deleteNode(selectedNodeId)
  }, [selectedNodeId, deleteNode])

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
    markDirty()
  }, [setNodes, markDirty, pushHistorySnapshot])

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

    markDirty()
  }, [setNodes, setEdges, markDirty])

  const updateProcessStepField = useCallback((nodeId: string, field: string, value: string) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: applyProcessStepFieldUpdate(node.data, field, value) } : node)))
    markDirty()
  }, [setNodes, markDirty])

  const updateActionOnIngredients = useCallback((nodeId: string, ingredients: RecipeIngredient[]) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withProcessStepActionOnIngredients(node.data, ingredients) } : node)))
    markDirty()
  }, [setNodes, markDirty])

  const updateActionOnProcesses = useCallback((nodeId: string, processIds: number[]) => {
    setNodes((nds) => nds.map((node) => (node.id === nodeId ? { ...node, data: withProcessStepActionOnProcesses(node.data, processIds) } : node)))
    markDirty()
  }, [setNodes, markDirty])

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
    markDirty()
  }, [nodes, setEdges, markDirty, pushHistorySnapshot])

  const handleZoomIn = useCallback(() => {
    reactFlowInstance.current?.zoomIn()
  }, [])

  const handleZoomOut = useCallback(() => {
    reactFlowInstance.current?.zoomOut()
  }, [])

  const handleFitView = useCallback(() => {
    reactFlowInstance.current?.fitView()
  }, [])

  const handleViewportMove = useCallback((_event: unknown, viewport: Viewport) => {
    currentViewportRef.current = viewport
    setZoomPercent(Math.round(viewport.zoom * 100))
  }, [])

  const handleExport = useCallback(() => {
    if (!processMeta) return
    const viewport = currentViewportRef.current ?? reactFlowInstance.current?.getViewport()
    const flowData = createFlowDataPayload(nodes, edges, viewport ?? undefined)
    const payload = buildProcessUpdateRequest(processMeta.name, processMeta.description, flowData)
    setExportJson(JSON.stringify({ recipeId, processId, type: processMeta.type, ...payload }, null, 2))
  }, [nodes, edges, processMeta, recipeId, processId])

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

  // Non-AI "story mode" walkthrough of this process's steps, in flow order — no image generation
  // happens for the new Process model, so every slide is text-only (the slideshow component already
  // renders a placeholder when `imageUrl` is absent).
  const buildSlideshowSteps = useCallback((): SlideshowStep[] => {
    const stepNodes = nodes.filter(isProcessStepNode)
    return stepNodes.map((node, index) => {
      const normalized = normalizeProcessStepNodeData(node.data)
      const { step } = normalized
      const ingredientNames = step.actionOn.ingredients
        .map((entry) => ingredientCatalog.find((item) => item.id === entry.ingredientId)?.name)
        .filter((name): name is string => Boolean(name))
      const subprocessNames = step.actionOn.processes
        .map((entry) => recipeProcesses.find((candidate) => candidate.id === entry.processId)?.name)
        .filter((name): name is string => Boolean(name))
      const descriptionParts = [
        ingredientNames.length > 0 ? `On: ${ingredientNames.join(', ')}` : '',
        subprocessNames.length > 0 ? `Using: ${subprocessNames.join(', ')}` : '',
        getProcessStepDurationLabel(step),
        step.temperature,
        step.notes,
      ].filter(Boolean)

      return {
        id: node.id,
        title: normalized.title,
        description: descriptionParts.join(' · ') || undefined,
        stepNumber: index + 1,
      }
    })
  }, [nodes, ingredientCatalog, recipeProcesses])

  /** Guards any navigation away from this process (Back button, breadcrumb clicks, opening a subprocess) behind the same unsaved-changes confirmation. */
  const confirmNavigatingAway = useCallback(() => !dirty || window.confirm('You have unsaved changes. Leave without saving?'), [dirty])

  /** Called from ProcessStepPanel's "Open" button on a referenced subprocess — the only way to open a subprocess from inside a process's own canvas, now that there's no PROCESS node to double-click. */
  const handleOpenSubprocess = useCallback((subprocessId: number) => {
    if (!processMeta) return
    if (!confirmNavigatingAway()) return
    onOpenSubprocess(subprocessId, processMeta.name)
  }, [processMeta, confirmNavigatingAway, onOpenSubprocess])

  const handleSave = useCallback(async () => {
    if (!processMeta) return
    setSaving(true)
    setSaveError(null)

    try {
      const viewport = currentViewportRef.current ?? reactFlowInstance.current?.getViewport()
      const flowData = createFlowDataPayload(nodes, edges, viewport ?? undefined)
      const payload = buildProcessUpdateRequest(processMeta.name, processMeta.description, flowData)
      const updated = await ProcessApi.update(recipeId, processId, payload)

      const refreshedProcesses = await ProcessApi.listByRecipe(recipeId)
      const nextFlowData = processToFlowData(updated)

      setNodes(nextFlowData.nodes.map(normalizeFlowNode))
      setEdges(normalizeFlowEdges(nextFlowData.edges))
      setRecipeProcesses(refreshedProcesses)
      setProcessMeta({ name: updated.name, description: updated.description, type: updated.type })
      currentViewportRef.current = nextFlowData.viewport ?? null
      setDirty(false)
      notifySuccess('Process saved')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to save this process right now'
      setSaveError(message)
      notifyError(message)
    } finally {
      setSaving(false)
    }
  }, [nodes, edges, processMeta, recipeId, processId, setNodes, setEdges, notifySuccess, notifyError])

  const handleBack = useCallback(() => {
    if (confirmNavigatingAway()) onBack?.()
  }, [confirmNavigatingAway, onBack])

  const handleNavigateToList = useCallback(() => {
    if (confirmNavigatingAway()) onNavigateToList()
  }, [confirmNavigatingAway, onNavigateToList])

  const handleNavigateToAncestor = useCallback((index: number) => {
    if (confirmNavigatingAway()) onNavigateToAncestor(index)
  }, [confirmNavigatingAway, onNavigateToAncestor])

  if (loading) {
    return (
      <div className="flex h-full w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)' }}>
        Loading process…
      </div>
    )
  }

  if (loadError || !processMeta) {
    return (
      <div className="flex h-full w-full flex-col items-center justify-center gap-3" style={{ color: 'var(--flow-text-muted)' }}>
        <div>{loadError ?? 'This process could not be loaded.'}</div>
        {onBack && (
          <button onClick={onBack} style={{ padding: '8px 14px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', cursor: 'pointer' }}>
            ← Back
          </button>
        )}
      </div>
    )
  }

  const selectedNode = nodes.find((node) => String(node.id) === String(selectedNodeId)) ?? null
  const availableSubprocesses = recipeProcesses.filter((process) => process.type === 'SUBPROCESS' && process.id !== processId)

  return (
    <div className="flex h-full w-full flex-col">
      <ProcessTopBar
        name={processMeta.name}
        processType={processMeta.type}
        breadcrumbAncestors={breadcrumbAncestors}
        onNavigateToList={handleNavigateToList}
        onNavigateToAncestor={handleNavigateToAncestor}
        onBack={onBack ? handleBack : undefined}
        onSave={handleSave}
        isSaving={saving}
        isDirty={dirty}
        saveError={saveError}
        onUndo={undo}
        onRedo={redo}
        canUndo={history.length > 0}
        canRedo={future.length > 0}
        onDeleteSelected={selectedNodeId ? deleteSelected : undefined}
        zoomPercent={zoomPercent}
        onZoomIn={handleZoomIn}
        onZoomOut={handleZoomOut}
        onFitView={handleFitView}
        onVisualize={() => setShowSlideshow(true)}
        onExport={handleExport}
      />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <div
          style={{
            width: sidebarCollapsed ? 34 : 260,
            flexShrink: 0,
            borderRight: '1px solid var(--flow-border)',
            display: 'flex',
            flexDirection: 'column',
            minHeight: 0,
            overflow: 'hidden',
            transition: 'width 0.15s ease',
          }}
        >
          <button
            type="button"
            onClick={() => setSidebarCollapsed((value) => !value)}
            title={sidebarCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            style={{
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: sidebarCollapsed ? 'center' : 'flex-end',
              padding: '6px 8px',
              border: 'none',
              borderBottom: '1px solid var(--flow-border)',
              background: 'var(--flow-surface)',
              color: 'var(--flow-text-muted)',
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            {sidebarCollapsed ? '▶' : '◀ Collapse'}
          </button>

          {!sidebarCollapsed && (
            sidebarHeader ? (
              <>
                <div style={{ flex: '1 1 50%', minHeight: 0, overflow: 'hidden', borderBottom: '1px solid var(--flow-border)', display: 'flex', flexDirection: 'column' }}>
                  {sidebarHeader}
                </div>
                <div style={{ flex: '1 1 50%', minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                  <ProcessSidebar
                    onAddNode={addNode}
                    nodes={nodes}
                    edges={edges}
                    selectedNodeId={selectedNodeId}
                    onSelectNode={setSelectedNodeId}
                  />
                </div>
              </>
            ) : (
              <div style={{ flex: 1, minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                <ProcessSidebar
                  onAddNode={addNode}
                  nodes={nodes}
                  edges={edges}
                  selectedNodeId={selectedNodeId}
                  onSelectNode={setSelectedNodeId}
                />
              </div>
            )
          )}
        </div>

        <div className="relative min-w-0 flex-1">
          {/* STEP nodes need ingredient/subprocess names to show Action On info directly on the
              canvas (per the brief) — provided via context rather than extra node props, since
              React Flow's custom node components only receive their own node's data. */}
          <ProcessGraphProvider value={{ ingredientCatalog, availableSubprocesses }}>
            <ReactFlow
              nodes={nodes}
              edges={edges}
              onNodesChange={handleNodesChange}
              onEdgesChange={handleEdgesChange}
              onConnect={onConnect}
              isValidConnection={isValidConnection}
              onSelectionChange={({ nodes: selected }) => setSelectedNodeId(selected[0]?.id ?? null)}
              nodeTypes={nodeTypes}
              onInit={(instance) => { reactFlowInstance.current = instance }}
              onMove={handleViewportMove}
              // defaultViewport only takes effect at this initial mount (ReactFlow only mounts once
              // `loading` is false, i.e. once we already know whether a saved viewport exists) — a
              // persisted viewport is restored exactly; otherwise fall back to auto-fitting content.
              defaultViewport={initialViewport ?? undefined}
              fitView={!initialViewport}
              panOnScroll
              selectionOnDrag
            >
              <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
              <MiniMap pannable zoomable />
            </ReactFlow>
          </ProcessGraphProvider>
        </div>

        <div style={{ width: 280, flexShrink: 0, borderLeft: '1px solid var(--flow-border)', overflowY: 'auto' }}>
          {selectedNode && isProcessStepNode(selectedNode) ? (
            <ProcessStepPanel
              node={{ id: selectedNode.id, data: selectedNode.data }}
              ingredientCatalog={ingredientCatalog}
              availableSubprocesses={availableSubprocesses}
              updateStepField={updateProcessStepField}
              updateActionOnIngredients={updateActionOnIngredients}
              updateActionOnProcesses={updateActionOnProcesses}
              onDeleteNode={deleteNode}
              onDuplicateNode={duplicateNode}
              onOpenSubprocess={handleOpenSubprocess}
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
