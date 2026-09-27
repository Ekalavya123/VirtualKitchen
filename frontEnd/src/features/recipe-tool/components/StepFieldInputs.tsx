import type { CSSProperties } from 'react'
import SearchableSelect, { type SearchableSelectOption } from '../../../shared/components/SearchableSelect'
import { getAllowedPreparationStyles } from '../catalog/actionSchemaCatalog'
import type { StepActionId } from '../catalog/actionCatalog'
import { getIngredientById, getIngredientUnitIds, CUSTOM_INGREDIENT_ID, type IngredientId } from '../catalog/ingredientCatalog'
import {
  CUSTOM_PREPARATION_STYLE_ID,
  getPreparationStyleById,
} from '../catalog/preparationStyleCatalog'
import { UNIT_CATEGORY_ORDER, UNITS_BY_CATEGORY, getUnitById, getUnitCategoryLabel, type UnitId } from '../catalog/unitCatalog'

type UnitSelectProps = {
  ingredientId: IngredientId | ''
  value: UnitId | ''
  onChange: (unit: UnitId) => void
  style?: CSSProperties
}

/**
 * Unit picker for one Action On ingredient: the ingredient's own units first (its default, then the
 * alternatives the catalog lists for it), then every other catalog unit by category — so an unusual
 * unit is still possible without cluttering the common case.
 */
export function UnitSelect({ ingredientId, value, onChange, style }: UnitSelectProps) {
  const suggested = getIngredientUnitIds(ingredientId)
  const suggestedSet = new Set(suggested)
  const showSuggestedGroup = Boolean(ingredientId) && ingredientId !== CUSTOM_INGREDIENT_ID

  return (
    <select className="flow-properties-input" style={style} value={value} onChange={(e) => onChange(e.target.value)}>
      {!value && <option value="">Unit</option>}
      {showSuggestedGroup && (
        <optgroup label={`For ${getIngredientById(ingredientId).name}`}>
          {suggested.map((unitId) => (
            <option key={unitId} value={unitId}>{getUnitById(unitId).label}</option>
          ))}
        </optgroup>
      )}
      {UNIT_CATEGORY_ORDER.map((category) => {
        const units = UNITS_BY_CATEGORY[category].filter((unit) => !showSuggestedGroup || !suggestedSet.has(unit.id))
        if (units.length === 0) return null
        return (
          <optgroup key={category} label={getUnitCategoryLabel(category)}>
            {units.map((unit) => (
              <option key={unit.id} value={unit.id}>{unit.label}</option>
            ))}
          </optgroup>
        )
      })}
    </select>
  )
}

/** The preparation styles valid for this action + ingredient (plus custom), keeping a current value visible even if the rules changed since it was set. */
const buildPreparationStyleOptions = (
  action: StepActionId | '',
  ingredientId: IngredientId | '',
  currentValue = '',
): SearchableSelectOption[] => {
  const allowed = getAllowedPreparationStyles(action, ingredientId)
  if (allowed.length === 0 && !currentValue) return []
  const ids = [...allowed]
  if (currentValue && currentValue !== CUSTOM_PREPARATION_STYLE_ID && !ids.includes(currentValue)) ids.push(currentValue)
  return [
    ...ids.map((id) => ({ value: id, label: getPreparationStyleById(id).label })),
    { value: CUSTOM_PREPARATION_STYLE_ID, label: getPreparationStyleById(CUSTOM_PREPARATION_STYLE_ID).label },
  ]
}

type PreparationStyleSelectProps = {
  action: StepActionId | ''
  ingredientId: IngredientId | ''
  value: string
  customValue: string
  onChange: (preparationStyleId: string, customPreparationStyle: string) => void
}

/** Renders nothing when the action/ingredient combination takes no preparation style (e.g. Stir, or Cut + Water). */
export function PreparationStyleSelect({ action, ingredientId, value, customValue, onChange }: PreparationStyleSelectProps) {
  const options = buildPreparationStyleOptions(action, ingredientId, value)
  if (options.length === 0) return null

  return (
    <>
      <div style={{ flex: 1, minWidth: 140 }}>
        <SearchableSelect
          value={value}
          onChange={(next) => onChange(next, next === CUSTOM_PREPARATION_STYLE_ID ? customValue : '')}
          options={options}
          placeholder="Preparation style"
        />
      </div>
      {value === CUSTOM_PREPARATION_STYLE_ID && (
        <input
          className="flow-properties-input"
          style={{ flex: 1, minWidth: 120 }}
          value={customValue}
          onChange={(e) => onChange(CUSTOM_PREPARATION_STYLE_ID, e.target.value)}
          placeholder="Custom preparation style"
        />
      )}
    </>
  )
}
