import { useState } from 'react'
import '../styles/recipe-tool.css'
import '../process/styles/RecipePropertiesPanel.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../shared/components/SearchableSelect'
import {
  CUSTOM_INGREDIENT_ID,
  INGREDIENTS_BY_CATEGORY,
  INGREDIENT_CATEGORY_ORDER,
  getIngredientDefaultUnitType,
  isIngredientId,
  type IngredientId,
} from '../catalog/ingredientCatalog'
import { CUSTOM_PREPARATION_STYLE_ID, DEFAULT_PREPARATION_STYLE_ID, PREPARATION_STYLE_CATALOG } from '../catalog/preparationStyleCatalog'
import { isStepFieldEnabled } from '../catalog/actionSchemaCatalog'
import type { StepActionId } from '../catalog/actionCatalog'
import type { ActionOnIngredient } from '../process/model/recipeStepData'
import type { UnitType } from '../../../types/recipe'

const UNIT_OPTIONS: { value: UnitType; label: string }[] = [
  { value: 'COUNT', label: 'count' },
  { value: 'GRAM', label: 'g' },
  { value: 'KG', label: 'kg' },
  { value: 'ML', label: 'mL' },
  { value: 'LITER', label: 'L' },
]

const preparationStyleOptions: SearchableSelectOption[] = PREPARATION_STYLE_CATALOG.map((style) => ({ value: style.id, label: style.label }))

type IngredientSelectorProps = {
  /** Ingredient ids already on this step, excluded from the picker so the same ingredient isn't added twice. */
  excludeIngredientIds: IngredientId[]
  /** The step's current action — decides whether preparation style is relevant at all (e.g. Cut vs. Boil), reusing the existing action schema rather than a new rule. */
  action: StepActionId | ''
  onAdd: (ingredient: ActionOnIngredient) => void
}

/**
 * Small "add an ingredient" row for a step's Action On: pick from the app's
 * static UI ingredient catalog (catalog/ingredientCatalog.ts) — not a
 * backend catalog API — then set this step's own quantity/unit/notes
 * /preparation for it. The catalog's "custom" entry isn't offered here:
 * Action On has no free-text ingredient name field.
 */
export default function IngredientSelector({ excludeIngredientIds, action, onAdd }: IngredientSelectorProps) {
  const preparationStyleRelevant = isStepFieldEnabled(action, 'preparationStyleId')

  const [ingredientIdValue, setIngredientIdValue] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [unit, setUnit] = useState<UnitType>('COUNT')
  const [notes, setNotes] = useState('')
  const [preparationStyleId, setPreparationStyleId] = useState('')
  const [customPreparationStyle, setCustomPreparationStyle] = useState('')
  const [error, setError] = useState<string | null>(null)

  const options: SearchableSelectOption[] = INGREDIENT_CATEGORY_ORDER.flatMap((category) =>
    INGREDIENTS_BY_CATEGORY[category]
      .filter((item) => item.id !== CUSTOM_INGREDIENT_ID && !excludeIngredientIds.includes(item.id))
      .map((item) => ({ value: item.id, label: item.name, icon: item.icon, category }))
  )

  // Pre-fills the unit from the ingredient's own catalog default (falling back to the existing
  // COUNT default when the catalog default doesn't map onto the Process model's unit enum) and the
  // preparation style to `medium` when relevant to the current action — deliberately applied here,
  // at the moment an ingredient is actually picked, rather than as each field's useState initial
  // value: this form doesn't remount when the step's action changes (it's the same still-open "add
  // ingredient" row), so a useState initial value computed from `preparationStyleRelevant` would
  // only ever reflect whatever the action was when the row first mounted, not its current value.
  const handleSelectIngredient = (value: string) => {
    setIngredientIdValue(value)
    const defaultUnit = isIngredientId(value) ? getIngredientDefaultUnitType(value) : null
    if (defaultUnit) setUnit(defaultUnit)
    setPreparationStyleId(preparationStyleRelevant ? DEFAULT_PREPARATION_STYLE_ID : '')
  }

  const handleAdd = () => {
    if (!isIngredientId(ingredientIdValue) || ingredientIdValue === CUSTOM_INGREDIENT_ID) {
      setError('Choose an ingredient')
      return
    }

    const quantityNumber = Number(quantity)
    if (!quantity.trim() || !Number.isFinite(quantityNumber) || quantityNumber < 0) {
      setError('Enter a valid quantity')
      return
    }

    onAdd({
      ingredientId: ingredientIdValue,
      quantity: quantityNumber,
      unit,
      notes: notes.trim() || undefined,
      preparationStyleId: preparationStyleRelevant ? (preparationStyleId as ActionOnIngredient['preparationStyleId']) : '',
      customPreparationStyle: preparationStyleRelevant && preparationStyleId === CUSTOM_PREPARATION_STYLE_ID ? customPreparationStyle.trim() : undefined,
    })

    setIngredientIdValue('')
    setQuantity('1')
    setUnit('COUNT')
    setNotes('')
    setPreparationStyleId(preparationStyleRelevant ? DEFAULT_PREPARATION_STYLE_ID : '')
    setCustomPreparationStyle('')
    setError(null)
  }

  return (
    <div className="flex flex-col gap-2 rounded-xl border p-3" style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface-muted)' }}>
      <div className="flex flex-wrap items-end gap-2">
        <div style={{ minWidth: 200, flex: 1 }}>
          <label className="flow-properties-label">Ingredient</label>
          <SearchableSelect
            value={ingredientIdValue}
            onChange={handleSelectIngredient}
            options={options}
            placeholder={options.length ? 'Select an ingredient' : 'No more ingredients in the catalog'}
          />
        </div>
        <div style={{ width: 90 }}>
          <label className="flow-properties-label">Quantity</label>
          <input className="flow-properties-input" value={quantity} onChange={(e) => setQuantity(e.target.value)} placeholder="2" />
        </div>
        <div style={{ width: 90 }}>
          <label className="flow-properties-label">Unit</label>
          <select className="flow-properties-input" value={unit} onChange={(e) => setUnit(e.target.value as UnitType)}>
            {UNIT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        {preparationStyleRelevant && (
          <div style={{ flex: 1, minWidth: 160 }}>
            <SearchableSelect
              value={preparationStyleId}
              onChange={setPreparationStyleId}
              options={preparationStyleOptions}
              placeholder="Preparation style"
            />
          </div>
        )}
        {preparationStyleRelevant && preparationStyleId === CUSTOM_PREPARATION_STYLE_ID && (
          <input
            className="flow-properties-input"
            style={{ flex: 1, minWidth: 160 }}
            value={customPreparationStyle}
            onChange={(e) => setCustomPreparationStyle(e.target.value)}
            placeholder="Enter custom preparation style"
          />
        )}
        <input
          className="flow-properties-input"
          style={{ flex: 1, minWidth: 160 }}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes — optional"
        />
      </div>

      {error && <div style={{ fontSize: 12, color: '#dc2626', fontWeight: 600 }}>{error}</div>}

      <button
        type="button"
        onClick={handleAdd}
        style={{ alignSelf: 'flex-start', padding: '6px 14px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}
      >
        + Add Ingredient
      </button>
    </div>
  )
}
