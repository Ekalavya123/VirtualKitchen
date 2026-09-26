import { useCallback, useState } from 'react'
import type { CSSProperties } from 'react'
import { Handle, Position, NodeResizeControl, useNodeId, useUpdateNodeInternals } from '@xyflow/react'
import '../../styles/recipe-tool.css'
import { normalizeRecipeStepNodeData, getRecipeStepDurationLabel, type RecipeStepNodeData } from '../model/recipeStepData'
import { useRecipeProcessGraphContext } from '../context/RecipeProcessGraphContext'
import { getStepActionById, type ActionCategory, type StepActionId } from '../../catalog/actionCatalog'
import { getIngredientById } from '../../catalog/ingredientCatalog'
import { getPreparationStyleDisplayName } from '../../catalog/preparationStyleCatalog'
import { getFlameLevelDisplayName } from '../../catalog/flameLevelCatalog'

type CategoryTheme = {
  emoji: string
  accent: string
  accentStrong: string
  soft: string
  border: string
}

// Per-action-category colors of a recipe step node.
const CATEGORY_THEMES: Record<ActionCategory, CategoryTheme> = {
  'Ingredient Operations': { emoji: '🧺', accent: '#2563eb', accentStrong: '#1d4ed8', soft: '#eff6ff', border: '#bfdbfe' },
  'Preparation Operations': { emoji: '🔪', accent: '#7c3aed', accentStrong: '#6d28d9', soft: '#f5f3ff', border: '#ddd6fe' },
  'Cooking Operations': { emoji: '🔥', accent: '#ea580c', accentStrong: '#c2410c', soft: '#fff7ed', border: '#fed7aa' },
  'Mixing Operations': { emoji: '🥣', accent: '#0d9488', accentStrong: '#0f766e', soft: '#f0fdfa', border: '#99f6e4' },
  'Waiting Operations': { emoji: '⏳', accent: '#64748b', accentStrong: '#475569', soft: '#f1f5f9', border: '#e2e8f0' },
  'Finish Operations': { emoji: '🍽️', accent: '#16a34a', accentStrong: '#15803d', soft: '#f0fdf4', border: '#bbf7d0' },
  'Custom': { emoji: '✨', accent: '#94a3b8', accentStrong: '#64748b', soft: '#f8fafc', border: '#e2e8f0' },
}

const DEFAULT_THEME: CategoryTheme = { emoji: '🍳', accent: '#94a3b8', accentStrong: '#64748b', soft: '#f8fafc', border: '#e2e8f0' }

const getCategoryTheme = (action: StepActionId | ''): CategoryTheme => {
  if (!action) return DEFAULT_THEME
  return CATEGORY_THEMES[getStepActionById(action).category] ?? DEFAULT_THEME
}

const toNumber = (value: unknown, fallback: number) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

type RecipeStepNodeProps = {
  selected: boolean
  style?: CSSProperties
  width?: number
  height?: number
  data: RecipeStepNodeData
}

const MIN_WIDTH = 240
const MIN_HEIGHT = 140
const MAX_WIDTH = 460
const MAX_HEIGHT = 560

/** Action On lines shown before collapsing into "+N more" (a compact summary, not the full detail — that stays in the Step Properties panel). */
const COLLAPSED_ACTION_ON_LIMIT = 3

const UNIT_LABELS: Record<string, string> = { COUNT: '', GRAM: 'g', KG: 'kg', ML: 'mL', LITER: 'L' }

export default function RecipeStepNode({ selected, style: nodeStyle, width: nodeWidth, height: nodeHeight, data }: RecipeStepNodeProps) {
  const nodeId = useNodeId()
  const updateNodeInternals = useUpdateNodeInternals()
  const { availableSubprocesses, stepOrder, onNodeResizeStart, onNodeResizeEnd } = useRecipeProcessGraphContext()
  const [expanded, setExpanded] = useState(false)
  const normalized = normalizeRecipeStepNodeData(data)
  const step = normalized.step
  const theme = getCategoryTheme(step.action)
  const width = Math.min(Math.max(toNumber(nodeWidth, toNumber(nodeStyle?.width, 280)), MIN_WIDTH), MAX_WIDTH)
  const minHeight = Math.max(toNumber(nodeHeight, toNumber(nodeStyle?.height, 160)), MIN_HEIGHT)

  const stepIndex = nodeId ? stepOrder.indexOf(nodeId) : -1
  const stepNumber = stepIndex >= 0 ? stepIndex + 1 : data.stepNumber

  const imageUrl = normalized.visualization?.imageUrl
  // The image sits to the left of Action On/Description, sized off the card's own width rather
  // than a rigid fraction — object-fit: contain (not cover) so the whole generated image is always
  // visible, never cropped; a slightly wider-than-tall box keeps letterboxing minimal for typical
  // landscape generations without cropping portrait/square ones either.
  const imageBoxWidth = imageUrl ? Math.round(Math.min(170, Math.max(96, width * 0.36))) : 0
  const imageBoxHeight = Math.round(imageBoxWidth * 0.8)

  // Keeps the edge handles anchored correctly on the resized box — same pattern as
  // RecipeStepNode/RecipeConditionNode's own resize handling.
  const syncNodeLayout = useCallback(() => {
    if (nodeId) updateNodeInternals(nodeId)
  }, [nodeId, updateNodeInternals])

  const ingredientLines = step.actionOn.ingredients.map((entry, index) => {
    const name = getIngredientById(entry.ingredientId).name
    const unit = UNIT_LABELS[entry.unit] ?? entry.unit.toLowerCase()
    const quantity = [entry.quantity, unit].filter(Boolean).join(unit ? '' : ' ')
    const prepStyle = getPreparationStyleDisplayName(entry.preparationStyleId ?? '', entry.customPreparationStyle)
    return {
      key: `ingredient-${index}`,
      content: (
        <>
          <span style={{ fontWeight: 700, color: theme.accentStrong }}>{name}</span>
          {' × '}
          <span
            style={{
              fontWeight: 700, fontSize: 10, color: theme.accentStrong, background: theme.soft,
              border: `1px solid ${theme.border}`, borderRadius: 999, padding: '0 6px',
            }}
          >
            {quantity}
          </span>
          {prepStyle && <span style={{ color: 'var(--flow-text-muted)', fontWeight: 500 }}> ({prepStyle})</span>}
        </>
      ),
    }
  })

  const subprocessLines = step.actionOn.processes.map((entry) => {
    const process = availableSubprocesses.find((candidate) => candidate.id === entry.processId)
    const name = process ? process.name : `Process #${entry.processId}`
    return {
      key: `subprocess-${entry.processId}`,
      content: <span style={{ fontWeight: 700, color: theme.accentStrong }}>{name}</span>,
    }
  })

  const actionOnLines = [...ingredientLines, ...subprocessLines]
  const visibleLines = expanded ? actionOnLines : actionOnLines.slice(0, COLLAPSED_ACTION_ON_LIMIT)
  const hiddenCount = actionOnLines.length - visibleLines.length

  const flameLevel = getFlameLevelDisplayName(step.flameLevelId, step.customFlameLevel)
  const duration = getRecipeStepDurationLabel(step)

  const detailBadges = [
    step.temperature && { icon: '🌡️', label: step.temperature },
    flameLevel && { icon: '🔥', label: flameLevel },
    duration && { icon: '⏱️', label: duration },
  ].filter((entry): entry is { icon: string; label: string } => Boolean(entry))

  return (
    <div
      style={{
        width,
        minWidth: MIN_WIDTH,
        maxWidth: MAX_WIDTH,
        minHeight,
        background: 'var(--flow-surface)',
        borderRadius: 14,
        borderTop: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderRight: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderBottom: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderLeft: `5px solid ${theme.accent}`,
        boxShadow: selected
          ? `0 0 0 3px ${theme.accent}26, 0 8px 20px rgba(15, 23, 42, 0.12)`
          : '0 2px 10px rgba(15, 23, 42, 0.08)',
        padding: '12px 14px',
        boxSizing: 'border-box',
        display: 'flex',
        flexDirection: 'column',
        gap: 8,
        position: 'relative',
      }}
      className="flow-editor-surface process-step-node"
    >
      {selected && (
        <NodeResizeControl
          nodeId={nodeId ?? undefined}
          minWidth={MIN_WIDTH}
          minHeight={MIN_HEIGHT}
          maxWidth={MAX_WIDTH}
          maxHeight={MAX_HEIGHT}
          onResizeStart={() => { if (nodeId) onNodeResizeStart?.(nodeId) }}
          onResize={() => syncNodeLayout()}
          onResizeEnd={() => {
            syncNodeLayout()
            if (nodeId) onNodeResizeEnd?.(nodeId)
          }}
          position="bottom-right"
          style={{
            background: theme.accent,
            border: '2px solid #fff',
            borderRadius: '999px',
            width: 14,
            height: 14,
            boxShadow: `0 0 0 2px ${theme.accent}40`,
            zIndex: 30,
          }}
        />
      )}
      <Handle
        type="target"
        position={Position.Top}
        style={{ width: 10, height: 10, background: theme.accent, border: '2px solid white', boxShadow: `0 0 0 1.5px ${theme.accent}`, left: '50%', transform: 'translate(-50%, -50%)', zIndex: 20 }}
      />

      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div
          style={{
            width: 24, height: 24, borderRadius: '50%', flexShrink: 0,
            background: `linear-gradient(135deg, ${theme.accent}, ${theme.accentStrong})`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 12, fontWeight: 800, color: 'white', boxShadow: `0 2px 6px ${theme.accent}55`,
          }}
          aria-hidden
        >
          {stepNumber ?? theme.emoji}
        </div>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div style={{ fontWeight: 700, fontSize: 13, color: theme.accentStrong, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {normalized.title}
          </div>
          <div style={{ fontSize: 9.5, color: 'var(--flow-text-subtle)', fontWeight: 600 }}>
            {stepNumber != null ? `Step ${stepNumber}` : 'Step'}
          </div>
        </div>
      </div>

      {/* Image (when generated) on the left — object-fit: contain so the whole image is always
          visible, never cropped — with Action On + Description alongside it on the right, up to
          some point; Expected Output is deliberately pulled out below, full width (see further down). */}
      <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
        {imageUrl && (
          <div
            style={{
              width: imageBoxWidth, height: imageBoxHeight, borderRadius: 10, overflow: 'hidden', flexShrink: 0,
              border: `1px solid ${theme.border}`, background: '#0f172a',
            }}
          >
            <img src={imageUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'contain', display: 'block' }} />
          </div>
        )}

        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
          {actionOnLines.length > 0 ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
              <div style={{ fontSize: 9.5, fontWeight: 700, color: 'var(--flow-text-subtle)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>On:</div>
              {visibleLines.map((line) => (
                <div key={line.key} style={{ fontSize: 11, color: 'var(--flow-text)', display: 'flex', gap: 4, lineHeight: 1.35 }}>
                  <span style={{ color: theme.accentStrong, flexShrink: 0 }}>•</span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{line.content}</span>
                </div>
              ))}
              {hiddenCount > 0 && (
                <button
                  type="button"
                  className="nodrag"
                  onClick={(event) => {
                    event.stopPropagation()
                    setExpanded(true)
                  }}
                  style={{ alignSelf: 'flex-start', border: 'none', background: 'transparent', padding: 0, fontSize: 10.5, fontWeight: 700, color: theme.accentStrong, cursor: 'pointer' }}
                >
                  +{hiddenCount} more
                </button>
              )}
              {expanded && actionOnLines.length > COLLAPSED_ACTION_ON_LIMIT && (
                <button
                  type="button"
                  className="nodrag"
                  onClick={(event) => {
                    event.stopPropagation()
                    setExpanded(false)
                  }}
                  style={{ alignSelf: 'flex-start', border: 'none', background: 'transparent', padding: 0, fontSize: 10.5, fontWeight: 700, color: 'var(--flow-text-subtle)', cursor: 'pointer' }}
                >
                  Show less
                </button>
              )}
            </div>
          ) : (
            <div style={{ fontSize: 10.5, color: 'var(--flow-text-subtle)', fontStyle: 'italic' }}>No Action On targets yet</div>
          )}

          {detailBadges.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {detailBadges.map((badge) => (
                <span key={badge.icon} style={{ fontSize: 10.5, fontWeight: 700, padding: '2px 7px', borderRadius: 999, background: 'var(--flow-surface-muted)', color: 'var(--flow-text-muted)', border: '1px solid var(--flow-border)' }}>
                  {badge.icon} {badge.label}
                </span>
              ))}
            </div>
          )}

          {step.actionDescription.trim() && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 3, borderTop: '1px dashed var(--flow-border)', paddingTop: 6 }}>
              <span style={{ fontSize: 9, fontWeight: 800, color: 'var(--flow-text)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
                Description
              </span>
              <span style={{ fontSize: 10, color: 'var(--flow-text-muted)', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                {step.actionDescription.trim()}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Expected Output — pulled out of the image/text row so it always reads full width. */}
      {step.expectedOutput.trim() && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 3, borderTop: '1px dashed var(--flow-border)', paddingTop: 6 }}>
          <span style={{ fontSize: 9, fontWeight: 800, color: 'var(--flow-text)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
            Expected
          </span>
          <span style={{ fontSize: 10, color: 'var(--flow-text-muted)', lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
            {step.expectedOutput.trim()}
          </span>
        </div>
      )}

      <Handle
        type="source"
        position={Position.Bottom}
        style={{ width: 10, height: 10, background: theme.accent, border: '2px solid white', boxShadow: `0 0 0 1.5px ${theme.accent}`, left: '50%', transform: 'translate(-50%, 50%)', zIndex: 20 }}
      />
    </div>
  )
}
