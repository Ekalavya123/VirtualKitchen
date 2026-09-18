import type { Edge, Node } from '@xyflow/react'
import '../sidebar/Sidebar.css'
import {
  FLOW_NODE_TYPES,
  type FlowNodeType,
  getFlowNodeDisplayLabel,
  isAnyStepNode,
  isConditionNode,
} from '../../model/flowNodeModel'

type ProcessSidebarProps = {
  onAddNode: (type: FlowNodeType) => void
  nodes: Node[]
  edges: Edge[]
  selectedNodeId?: string | null
  onSelectNode?: (id: string | null) => void
}

/**
 * "Tool Options" half of the Process Builder's left column: Step/Condition
 * quick-add (no parallel nodes, and no PROCESS node — a process graph only
 * ever contains STEP/CONDITION nodes; a subprocess is referenced from a
 * STEP's own Action On data instead) plus the step/node summary and node
 * list, mirroring the legacy Sidebar's look (same stylesheet/classes)
 * without touching that component, since it's hardcoded to the legacy
 * step/condition/parallel node set. Collapsing is handled one level up, for
 * the whole combined column (Process View + Tool Options) — see
 * ProcessCanvas.tsx.
 */
export default function ProcessSidebar({ onAddNode, nodes, edges, selectedNodeId, onSelectNode }: ProcessSidebarProps) {
  const stepNodes = nodes.filter(isAnyStepNode)
  const conditionNodes = nodes.filter(isConditionNode)

  return (
    <div className="flow-sidebar">
      <div className="flex-shrink-0 border-b border-[var(--flow-border)] px-3.5 py-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-lg bg-[linear-gradient(135deg,var(--flow-accent),var(--flow-accent-secondary))] text-sm text-white">🧩</div>
          <div className="flex flex-col">
            <div className="text-[0.72rem] font-semibold text-[var(--flow-text)]">Tool Options</div>
            <div className="text-[0.62rem] text-[var(--flow-text-muted)]">Steps, conditions &amp; subprocesses</div>
          </div>
        </div>
      </div>

      <div className="flex-shrink-0 px-2.5 pt-2.5">
        <div className="flow-editor-section-heading mb-2 px-1">Quick Add</div>
        <div className="mb-1.5 flex gap-1.5">
          <button
            onClick={() => onAddNode(FLOW_NODE_TYPES.processStep)}
            className="flow-editor-action-button flex-1 border-sky-200 bg-sky-50 px-2 py-2 text-[0.7rem] font-semibold text-sky-700"
            title="Add a step node"
          >
            + Step
          </button>
          <button
            onClick={() => onAddNode(FLOW_NODE_TYPES.condition)}
            className="flow-editor-action-button flex-1 border-emerald-200 bg-emerald-50 px-2 py-2 text-[0.7rem] font-semibold text-emerald-700"
            title="Add a condition node"
          >
            + Condition
          </button>
        </div>
      </div>

      <div className="flex-shrink-0 border-b border-[var(--flow-border)] px-2.5 py-2.5">
        <div className="flow-editor-section-heading mb-2 px-1">Process Overview</div>
        <div className="grid grid-cols-2 gap-2 rounded-xl border border-[var(--flow-border)] bg-[var(--flow-surface-muted)] p-2 text-[0.72rem] text-[var(--flow-text-muted)]">
          <div className="rounded-lg bg-[var(--flow-surface)] px-2 py-2">
            <div className="text-[0.62rem] uppercase tracking-[0.16em] text-[var(--flow-text-subtle)]">Steps</div>
            <div className="mt-1 text-base font-semibold text-[var(--flow-text)]">{stepNodes.length}</div>
          </div>
          <div className="rounded-lg bg-[var(--flow-surface)] px-2 py-2">
            <div className="text-[0.62rem] uppercase tracking-[0.16em] text-[var(--flow-text-subtle)]">Conditions</div>
            <div className="mt-1 text-base font-semibold text-[var(--flow-text)]">{conditionNodes.length}</div>
          </div>
          <div className="col-span-2 rounded-lg bg-[var(--flow-surface)] px-2 py-2">
            <div className="text-[0.62rem] uppercase tracking-[0.16em] text-[var(--flow-text-subtle)]">Connections</div>
            <div className="mt-1 text-base font-semibold text-[var(--flow-text)]">{edges.length}</div>
          </div>
        </div>
      </div>

      <div className="flex flex-1 flex-col overflow-y-auto px-2.5 pb-2.5 pt-2">
        <div className="flow-editor-section-heading mb-1.5 px-1">Nodes</div>
        <div className="flex flex-col gap-1">
          {nodes.map((node) => {
            const isSelected = String(node.id) === String(selectedNodeId)
            const label = getFlowNodeDisplayLabel(node)
            return (
              <button
                key={node.id}
                type="button"
                className={`cursor-pointer overflow-hidden text-ellipsis whitespace-nowrap rounded-lg px-2.5 py-2 text-left text-[0.7rem] font-semibold transition-all ${isSelected ? 'border border-[var(--flow-accent-soft-border)] bg-[var(--flow-accent-soft)] text-[var(--flow-text)]' : 'bg-[var(--flow-surface-muted)] text-[var(--flow-text-muted)] hover:bg-[var(--flow-border)] hover:text-[var(--flow-text)]'}`}
                title={String(label)}
                onClick={() => onSelectNode?.(isSelected ? null : String(node.id))}
              >
                {String(label)}
              </button>
            )
          })}
        </div>
      </div>
    </div>
  )
}
