import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import type { CSSProperties } from 'react'
import { Handle, Position, NodeResizeControl, useNodeId, useUpdateNodeInternals } from '@xyflow/react'
import '../styles/flow-editor.css'
import {
  getStepIngredientName,
  normalizeStepNodeData,
  type RecipeStepNodeData,
} from '../../../types/recipeFlow'
import { getVisibleStepDetailRows } from '../catalog/stepActionPresentation'
import { getStepActionById, type ActionCategory, type StepActionId } from '../catalog/actionCatalog'
import type { StepSchemaFieldKey } from '../catalog/actionSchemaCatalog'

type CategoryTheme = {
  emoji: string
  accent: string
  accentStrong: string
  soft: string
  border: string
}

// One cooking-themed color per action category so a node's palette instantly signals what kind of step it is.
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

const FIELD_ICONS: Partial<Record<StepSchemaFieldKey, string>> = {
  preparationStyleId: '🔪',
  flameLevelId: '🔥',
  temperature: '🌡️',
  duration: '⏱️',
  repeatInterval: '🔁',
}

const getCategoryTheme = (action: StepActionId | ''): CategoryTheme => {
  if (!action) return DEFAULT_THEME
  return CATEGORY_THEMES[getStepActionById(action).category] ?? DEFAULT_THEME
}

type RecipeStepNodeProps = {
  selected: boolean
  style?: CSSProperties
  width?: number
  height?: number
  data: RecipeStepNodeData
}

const toNumber = (value: unknown, fallback: number) => {
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'string') {
    const parsed = Number.parseFloat(value)
    if (Number.isFinite(parsed)) return parsed
  }
  return fallback
}

export default function RecipeStepNode({ selected, style: nodeStyle, data, width: nodeWidth, height: nodeHeight }: RecipeStepNodeProps) {
  const nodeId = useNodeId()
  const updateNodeInternals = useUpdateNodeInternals()
  const containerRef = useRef<HTMLDivElement | null>(null)
  const notesRef = useRef<HTMLDivElement | null>(null)
  const [isNotesExpanded, setIsNotesExpanded] = useState(false)
  const [shouldShowReadMore, setShouldShowReadMore] = useState(false)
  const [imageFailed, setImageFailed] = useState(false)
  const normalized = normalizeStepNodeData(data)
  const step = normalized.step
  const theme = useMemo(() => getCategoryTheme(step.action), [step.action])
  const width = toNumber(nodeWidth, toNumber(nodeStyle?.width, 320))
  const height = toNumber(nodeHeight, toNumber(nodeStyle?.height, 240))
  const notes = step.notes.trim()
  const ingredientName = getStepIngredientName(step).trim()
  const quantityLabel = [step.quantity.trim(), step.unit.trim()].filter(Boolean).join(' ')
  const detailRows = getVisibleStepDetailRows(step).filter((row) => row.key !== 'preparationStyleId')
  const preparationStyle = step.preparationStyle.trim()
  const imageUrl = !imageFailed ? normalized.visualization?.imageUrl : undefined
  const isGenerated = normalized.visualization?.status === 'generated'

  const minimumWidth = 260
  const minimumHeight = 230
  const computedWidth = Math.min(Math.max(width, minimumWidth), 520)
  const computedMinHeight = Math.max(height, minimumHeight)
  const scale = Math.min(Math.max(Math.sqrt((computedWidth * computedMinHeight) / (320 * 240)), 0.75), 1.6)

  const stepBadgeSize = Math.round(24 * scale)
  const titleFontSize = Math.max(11, Math.round(13 * scale))
  const stepLabelFontSize = Math.max(9, Math.round(9.5 * scale))
  const ingredientFontSize = Math.max(12, Math.round(14 * scale))
  const pillFontSize = Math.max(9, Math.round(10 * scale))
  const notesFontSize = Math.max(9, Math.round(10 * scale))
  const readMoreFontSize = Math.max(9, Math.round(10 * scale))
  const cardPaddingY = Math.round(12 * scale)
  const cardPaddingX = Math.round(14 * scale)
  const cardGap = Math.round(9 * scale)
  // Square-ish thumbnail (roughly matching typical generated image proportions) sized off the
  // card width, so object-fit: contain has little letterboxing to show around it.
  const imageBoxSize = Math.round(Math.min(148, Math.max(84, computedWidth * 0.32)))

  const syncNodeLayout = useCallback(() => {
    if (nodeId) {
      updateNodeInternals(nodeId)
    }

    const noteElement = notesRef.current
    if (!noteElement) {
      setShouldShowReadMore(false)
      return
    }

    setShouldShowReadMore(noteElement.scrollHeight > noteElement.clientHeight + 1)
  }, [nodeId, updateNodeInternals])

  useLayoutEffect(() => {
    syncNodeLayout()
  }, [syncNodeLayout, computedWidth, computedMinHeight, notes, detailRows.length])

  useEffect(() => {
    setImageFailed(false)
  }, [normalized.visualization?.imageUrl])

  useEffect(() => {
    const containerElement = containerRef.current
    if (!containerElement) return

    const observer = new ResizeObserver(() => {
      syncNodeLayout()
    })

    observer.observe(containerElement)
    if (notesRef.current) {
      observer.observe(notesRef.current)
    }

    return () => observer.disconnect()
  }, [syncNodeLayout])

  return (
    <div
      ref={containerRef}
      style={{
        width: computedWidth,
        minWidth: minimumWidth,
        maxWidth: 520,
        minHeight: computedMinHeight,
        background: 'var(--flow-surface)',
        borderRadius: 14,
        borderTop: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderRight: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderBottom: `1.5px solid ${selected ? theme.accent : 'var(--flow-border)'}`,
        borderLeft: `5px solid ${theme.accent}`,
        boxShadow: selected
          ? `0 0 0 3px ${theme.accent}26, 0 8px 20px rgba(15, 23, 42, 0.12)`
          : '0 2px 10px rgba(15, 23, 42, 0.08)',
        padding: `${cardPaddingY}px ${cardPaddingX}px`,
        transition: 'box-shadow 0.18s ease, border-color 0.18s ease',
        position: 'relative',
        boxSizing: 'border-box',
        overflow: 'visible',
        display: 'flex',
        flexDirection: 'column',
        gap: cardGap,
      }}
      className="flow-editor-surface recipe-step-node"
    >
      {selected && (
        <NodeResizeControl
          nodeId={nodeId ?? undefined}
          minWidth={minimumWidth}
          minHeight={minimumHeight}
          maxWidth={520}
          maxHeight={640}
          keepAspectRatio
          onResize={() => syncNodeLayout()}
          onResizeEnd={() => syncNodeLayout()}
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
        style={{
          width: 10,
          height: 10,
          background: theme.accent,
          border: '2px solid white',
          boxShadow: `0 0 0 1.5px ${theme.accent}`,
          left: '50%',
          transform: 'translate(-50%, -50%)',
        }}
      />

      {/* Header: step number, action icon + title, generated badge */}
      <div style={{ display: 'flex', alignItems: 'center', gap: Math.round(8 * scale) }}>
        <div
          style={{
            width: stepBadgeSize,
            height: stepBadgeSize,
            borderRadius: '50%',
            background: `linear-gradient(135deg, ${theme.accent}, ${theme.accentStrong})`,
            color: 'white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: Math.max(9, Math.round(11 * scale)),
            fontWeight: 800,
            flexShrink: 0,
            boxShadow: `0 2px 6px ${theme.accent}55`,
          }}
        >
          {data.stepNumber ?? '•'}
        </div>
        <span style={{ fontSize: Math.max(13, Math.round(16 * scale)), flexShrink: 0 }} aria-hidden>
          {theme.emoji}
        </span>
        <div style={{ minWidth: 0, flex: 1 }}>
          <div
            style={{
              fontWeight: 700,
              fontSize: titleFontSize,
              color: 'var(--flow-text)',
              letterSpacing: '-0.01em',
              lineHeight: 1.2,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {normalized.title || 'Select Action'}
          </div>
          <div style={{ fontSize: stepLabelFontSize, color: 'var(--flow-text-subtle)', fontWeight: 600, marginTop: 1 }}>
            {data.stepNumber !== undefined ? `Step ${data.stepNumber}` : 'Recipe Step'}
          </div>
        </div>
        <span
          title={isGenerated ? 'Image generated' : 'Image not generated yet'}
          style={{
            fontSize: Math.max(8, pillFontSize - 1),
            fontWeight: 700,
            padding: '2px 7px',
            borderRadius: 999,
            background: isGenerated ? 'var(--flow-success-soft)' : 'var(--flow-surface-muted)',
            color: isGenerated ? 'var(--flow-success)' : 'var(--flow-text-subtle)',
            border: `1px solid ${isGenerated ? 'var(--flow-success-border)' : 'var(--flow-border)'}`,
            flexShrink: 0,
            whiteSpace: 'nowrap',
          }}
        >
          {isGenerated ? '🖼️ Ready' : 'No image'}
        </span>
      </div>

      {/* Media row: image thumbnail on the left, step details stacked on the right */}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: Math.round(10 * scale) }}>
        <div
          style={{
            width: imageBoxSize,
            height: imageBoxSize,
            borderRadius: 10,
            overflow: 'hidden',
            border: `1px solid ${theme.border}`,
            background: imageUrl ? '#0f172a' : `linear-gradient(135deg, ${theme.soft}, var(--flow-surface))`,
            flexShrink: 0,
            position: 'relative',
          }}
        >
          {imageUrl ? (
            <img
              src={imageUrl}
              alt={normalized.title || 'Recipe step'}
              onError={() => setImageFailed(true)}
              style={{ width: '100%', height: '100%', objectFit: 'contain', objectPosition: 'center', display: 'block' }}
            />
          ) : (
            <div
              style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 2,
                color: theme.accentStrong,
                opacity: 0.55,
              }}
            >
              <span style={{ fontSize: Math.max(16, Math.round(22 * scale)) }} aria-hidden>{theme.emoji}</span>
              <span style={{ fontSize: Math.max(8, pillFontSize - 1), fontWeight: 700, textAlign: 'center' }}>No image yet</span>
            </div>
          )}
        </div>

        <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: Math.round(8 * scale) }}>
          {/* Ingredient emphasis */}
          {(ingredientName || quantityLabel) && (
            <div style={{ display: 'flex', alignItems: 'baseline', flexWrap: 'wrap', gap: 6 }}>
              {ingredientName && (
                <span style={{ fontSize: ingredientFontSize, fontWeight: 700, color: 'var(--flow-text)' }}>
                  {ingredientName}
                </span>
              )}
              {quantityLabel && (
                <span
                  style={{
                    fontSize: pillFontSize,
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 999,
                    background: theme.soft,
                    color: theme.accentStrong,
                    border: `1px solid ${theme.border}`,
                  }}
                >
                  {quantityLabel}
                </span>
              )}
              {preparationStyle && (
                <span style={{ fontSize: pillFontSize, color: 'var(--flow-text-muted)', fontWeight: 600 }}>
                  {FIELD_ICONS.preparationStyleId} {preparationStyle}
                </span>
              )}
            </div>
          )}

          {/* Duration / temperature / flame / repeat badges */}
          {detailRows.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {detailRows.map((row) => (
                <span
                  key={row.key}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 4,
                    fontSize: pillFontSize,
                    fontWeight: 700,
                    padding: '3px 8px',
                    borderRadius: 999,
                    background: 'var(--flow-surface-muted)',
                    color: 'var(--flow-text-muted)',
                    border: '1px solid var(--flow-border)',
                  }}
                >
                  <span aria-hidden>{FIELD_ICONS[row.key] ?? '•'}</span>
                  {row.value}
                </span>
              ))}
            </div>
          )}

          {/* Notes */}
          {notes && (
            <div style={{ borderTop: '1px dashed var(--flow-border)', paddingTop: Math.round(6 * scale) }}>
              <div
                ref={notesRef}
                style={{
                  fontSize: notesFontSize,
                  color: 'var(--flow-text-muted)',
                  lineHeight: 1.45,
                  overflow: 'hidden',
                  whiteSpace: 'pre-wrap',
                  display: isNotesExpanded ? 'block' : '-webkit-box',
                  WebkitBoxOrient: isNotesExpanded ? undefined : 'vertical',
                  WebkitLineClamp: isNotesExpanded ? undefined : 2,
                  wordBreak: 'break-word',
                  overflowWrap: 'anywhere',
                  width: '100%',
                }}
              >
                {notes}
              </div>
              {(shouldShowReadMore || isNotesExpanded) && (
                <button
                  type="button"
                  onClick={() => setIsNotesExpanded((value) => !value)}
                  className="nodrag"
                  style={{
                    marginTop: 4,
                    alignSelf: 'flex-start',
                    border: 'none',
                    background: 'transparent',
                    color: theme.accentStrong,
                    fontSize: readMoreFontSize,
                    fontWeight: 700,
                    cursor: 'pointer',
                    padding: 0,
                  }}
                >
                  {isNotesExpanded ? 'Read less' : 'Read more'}
                </button>
              )}
            </div>
          )}
        </div>
      </div>

      <Handle
        type="source"
        position={Position.Bottom}
        style={{
          width: 10,
          height: 10,
          background: theme.accent,
          border: '2px solid white',
          boxShadow: `0 0 0 1.5px ${theme.accent}`,
          left: '50%',
          transform: 'translate(-50%, 50%)',
        }}
      />
    </div>
  )
}