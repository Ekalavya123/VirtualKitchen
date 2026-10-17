import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { isStepFieldEnabled } from '../../../../../shared/catalog/actionSchemaCatalog'
import { CUSTOM_UNIT_ID, isQuantifiableUnit, type UnitId } from '../../../../../shared/catalog/unitCatalog'
import { getActionOnIngredientDisplayName, type ActionOnIngredient, type RecipeStepFields } from '../../../model/recipeStepData'
import { PreparationStyleSelect, UnitSelect } from '../StepFieldInputs'
import './annotations.css'

type IngredientAmountPopoverProps = {
  step: RecipeStepFields
  index: number
  anchor: DOMRect
  /** Writes the edited Action On entry (amounts live there, never in the description). */
  onChange: (index: number, patch: Partial<ActionOnIngredient>) => void
  onClose: () => void
}

/**
 * The amount of one annotated ingredient — quantity, unit (and preparation style when the action takes one) —
 * edited where the annotation is, writing straight to the step's Action On entry. Closes on Enter, Esc, Done or
 * a click elsewhere; every change is already applied.
 */
export default function IngredientAmountPopover({ step, index, anchor, onChange, onClose }: IngredientAmountPopoverProps) {
  const ref = useRef<HTMLDivElement>(null)
  const quantityRef = useRef<HTMLInputElement>(null)
  const entry = step.actionOn.ingredients[index]
  const [quantityText, setQuantityText] = useState(entry?.quantity != null ? String(entry.quantity) : '')

  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    quantityRef.current?.focus()
    quantityRef.current?.select()
    const onPointerDown = (event: PointerEvent) => {
      if (!ref.current?.contains(event.target as Node)) onCloseRef.current()
    }
    document.addEventListener('pointerdown', onPointerDown, true)
    return () => document.removeEventListener('pointerdown', onPointerDown, true)
  }, [])

  if (!entry) return null
  const quantityRelevant = !step.action || isStepFieldEnabled(step.action, 'quantity')
  const quantifiable = !entry.unit || isQuantifiableUnit(entry.unit)
  const top = Math.min(anchor.bottom + 6, window.innerHeight - 220)
  const left = Math.max(8, Math.min(anchor.left, window.innerWidth - 290))

  return createPortal(
    <div
      ref={ref}
      className="annotation-amount-popover nodrag nowheel nopan"
      style={{ top, left }}
      role="dialog"
      aria-label={`Amount of ${getActionOnIngredientDisplayName(entry)}`}
      onKeyDown={(event) => {
        event.stopPropagation()
        if (event.key === 'Escape' || (event.key === 'Enter' && (event.target as HTMLElement).tagName === 'INPUT')) {
          event.preventDefault()
          onClose()
        }
      }}
    >
      <div className="annotation-amount-title">{getActionOnIngredientDisplayName(entry)}</div>
      {quantityRelevant ? (
        <div className="annotation-amount-row">
          <input
            ref={quantityRef}
            className="flow-properties-input"
            style={{ width: 70 }}
            inputMode="decimal"
            disabled={!quantifiable}
            value={quantifiable ? quantityText : ''}
            placeholder={quantifiable ? 'qty' : '—'}
            aria-label="Quantity"
            onChange={(event) => {
              const text = event.target.value
              setQuantityText(text)
              const parsed = Number(text.trim())
              onChange(index, { quantity: text.trim() && Number.isFinite(parsed) ? parsed : null })
            }}
          />
          <UnitSelect
            ingredientId={entry.ingredientId}
            value={entry.unit}
            style={{ flex: 1, minWidth: 100 }}
            onChange={(unit: UnitId) => onChange(index, {
              unit,
              customUnit: unit === CUSTOM_UNIT_ID ? entry.customUnit ?? '' : undefined,
              ...(isQuantifiableUnit(unit) ? {} : { quantity: null }),
            })}
          />
          {entry.unit === CUSTOM_UNIT_ID && (
            <input
              className="flow-properties-input"
              style={{ width: '100%' }}
              value={entry.customUnit ?? ''}
              placeholder="Unit name (e.g. knob)"
              aria-label="Custom unit"
              onChange={(event) => onChange(index, { customUnit: event.target.value })}
            />
          )}
        </div>
      ) : (
        <div style={{ fontSize: 12, color: 'var(--flow-text-muted)' }}>This action doesn't take amounts.</div>
      )}
      <div className="annotation-amount-row">
        <PreparationStyleSelect
          action={step.action}
          ingredientId={entry.ingredientId}
          value={entry.preparationStyleId ?? ''}
          customValue={entry.customPreparationStyle ?? ''}
          onChange={(preparationStyleId, customPreparationStyle) => onChange(index, { preparationStyleId, customPreparationStyle })}
        />
      </div>
      <div className="annotation-amount-actions">
        <button type="button" className="flow-action-on-entry-btn" onClick={onClose}>Done</button>
      </div>
    </div>,
    document.body,
  )
}
