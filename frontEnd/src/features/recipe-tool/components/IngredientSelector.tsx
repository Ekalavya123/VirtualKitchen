import { useState } from 'react'
import '../styles/recipe-tool.css'
import '../process/styles/RecipePropertiesPanel.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../shared/components/SearchableSelect'
import {
  CUSTOM_INGREDIENT_ID,
  INGREDIENTS_BY_CATEGORY,
  INGREDIENT_CATEGORY_ORDER,
  getIngredientCategoryLabel,
  getIngredientDefaultUnit,
  isIngredientId,
  type IngredientId,
} from '../catalog/ingredientCatalog'
import { CUSTOM_PREPARATION_STYLE_ID, DEFAULT_PREPARATION_STYLE_ID } from '../catalog/preparationStyleCatalog'
import { getAllowedPreparationStyles, isStepFieldEnabled } from '../catalog/actionSchemaCatalog'
import { isQuantifiableUnit, type UnitId } from '../catalog/unitCatalog'
import type { StepActionId } from '../catalog/actionCatalog'
import type { ActionOnIngredient } from '../process/model/recipeStepData'
import { PreparationStyleSelect, UnitSelect } from './StepFieldInputs'
import { useDraft } from '../../../shared/drafts/useDraft'

// Built once: the full global catalog, grouped by category, searchable by aliases — never
// restricted to what the Kitchen inventory currently holds.
const ALL_INGREDIENT_OPTIONS: SearchableSelectOption[] = INGREDIENT_CATEGORY_ORDER.flatMap((category) =>
  INGREDIENTS_BY_CATEGORY[category].map((item) => ({
    value: item.id,
    label: item.name,
    icon: item.icon,
    category: getIngredientCategoryLabel(category),
    keywords: item.aliases,
  }))
)

type IngredientSelectorProps = {
  /** Ingredient ids already on this step, excluded from the picker so the same ingredient isn't added twice (custom ingredients excepted). */
  excludeIngredientIds: IngredientId[]
  /** The step's current action — decides which of quantity/unit/preparation style apply at all (catalog action schema). */
  action: StepActionId | ''
  onAdd: (ingredient: ActionOnIngredient) => void
  /** Where the half-filled row is kept until the ingredient is added (see shared/drafts); null keeps nothing. */
  draftKey?: string | null
}

type IngredientDraft = {
  ingredientIdValue: string
  customIngredientName: string
  quantity: string
  unit: UnitId
  notes: string
  preparationStyleId: string
  customPreparationStyle: string
}

const EMPTY_INGREDIENT_DRAFT: IngredientDraft = {
  ingredientIdValue: '',
  customIngredientName: '',
  quantity: '1',
  unit: 'piece',
  notes: '',
  preparationStyleId: '',
  customPreparationStyle: '',
}

/**
 * Small "add an ingredient" row for a step's Action On: pick from the app's shared ingredient
 * catalog (catalog/ingredientCatalog.ts, the same list the AI generator uses) or name a custom
 * ingredient, then set this step's own quantity/unit/preparation for it.
 */
export default function IngredientSelector({ excludeIngredientIds, action, onAdd, draftKey = null }: IngredientSelectorProps) {
  const quantityRelevant = isStepFieldEnabled(action, 'quantity')

  // What's typed here is kept as a draft until the ingredient is added, so selecting another step
  // or leaving the recipe and coming back doesn't lose it.
  const draft = useDraft<IngredientDraft>(draftKey, EMPTY_INGREDIENT_DRAFT)
  const { ingredientIdValue, customIngredientName, quantity, unit, notes, preparationStyleId, customPreparationStyle } = draft.value
  const field = <K extends keyof IngredientDraft>(key: K) => (value: IngredientDraft[K]) =>
    draft.setValue((current) => ({ ...current, [key]: value }))
  const setCustomIngredientName = field('customIngredientName')
  const setQuantity = field('quantity')
  const setUnit = field('unit')
  const setNotes = field('notes')
  const setPreparationStyleId = field('preparationStyleId')
  const setCustomPreparationStyle = field('customPreparationStyle')
  const [error, setError] = useState<string | null>(null)

  const options = ALL_INGREDIENT_OPTIONS.filter((option) => option.value === CUSTOM_INGREDIENT_ID || !excludeIngredientIds.includes(option.value))
  const selectedIngredientId: IngredientId | '' = isIngredientId(ingredientIdValue) ? ingredientIdValue : ''
  const unitQuantifiable = isQuantifiableUnit(unit)

  // Pre-fills the unit from the ingredient's own catalog default and the preparation style to
  // `medium` only when the current action allows it for this ingredient — deliberately applied
  // here, at the moment an ingredient is actually picked, rather than as each field's useState
  // initial value: this form doesn't remount when the step's action changes.
  const handleSelectIngredient = (value: string) => {
    const allowed = getAllowedPreparationStyles(action, value)
    draft.setValue((current) => ({
      ...current,
      ingredientIdValue: value,
      unit: getIngredientDefaultUnit(value),
      preparationStyleId: allowed.includes(DEFAULT_PREPARATION_STYLE_ID) ? DEFAULT_PREPARATION_STYLE_ID : '',
      customPreparationStyle: '',
    }))
  }

  const reset = () => {
    draft.clear()
    setError(null)
  }

  const handleAdd = () => {
    if (!selectedIngredientId) {
      setError('Choose an ingredient')
      return
    }
    if (selectedIngredientId === CUSTOM_INGREDIENT_ID && !customIngredientName.trim()) {
      setError('Enter a name for the custom ingredient')
      return
    }

    let quantityNumber: number | null = null
    if (quantityRelevant && unitQuantifiable && quantity.trim()) {
      quantityNumber = Number(quantity)
      if (!Number.isFinite(quantityNumber) || quantityNumber < 0) {
        setError('Enter a valid quantity')
        return
      }
    }

    const preparationRelevant = getAllowedPreparationStyles(action, selectedIngredientId).length > 0
    onAdd({
      ingredientId: selectedIngredientId,
      customIngredientName: selectedIngredientId === CUSTOM_INGREDIENT_ID ? customIngredientName.trim() : undefined,
      quantity: quantityNumber,
      unit: quantityRelevant ? unit : '',
      notes: notes.trim() || undefined,
      preparationStyleId: preparationRelevant ? preparationStyleId : '',
      customPreparationStyle: preparationRelevant && preparationStyleId === CUSTOM_PREPARATION_STYLE_ID ? customPreparationStyle.trim() : undefined,
    })
    reset()
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
            placeholder="Search ingredients (name or alias)"
          />
        </div>
        {quantityRelevant && (
          <>
            <div style={{ width: 80 }}>
              <label className="flow-properties-label">Quantity</label>
              <input
                className="flow-properties-input"
                value={unitQuantifiable ? quantity : ''}
                disabled={!unitQuantifiable}
                onChange={(e) => setQuantity(e.target.value)}
                placeholder={unitQuantifiable ? '2' : '—'}
              />
            </div>
            <div style={{ width: 120 }}>
              <label className="flow-properties-label">Unit</label>
              <UnitSelect ingredientId={selectedIngredientId} value={unit} onChange={setUnit} />
            </div>
          </>
        )}
      </div>

      {selectedIngredientId === CUSTOM_INGREDIENT_ID && (
        <input
          className="flow-properties-input"
          value={customIngredientName}
          onChange={(e) => setCustomIngredientName(e.target.value)}
          placeholder="Custom ingredient name"
        />
      )}

      <div className="flex flex-wrap gap-2">
        <PreparationStyleSelect
          action={action}
          ingredientId={selectedIngredientId}
          value={preparationStyleId}
          customValue={customPreparationStyle}
          onChange={(styleId, custom) => {
            setPreparationStyleId(styleId)
            setCustomPreparationStyle(custom)
          }}
        />
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
