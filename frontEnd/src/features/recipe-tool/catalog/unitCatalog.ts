import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

export type UnitCategory = string

/** A unit id from stepCatalogs.data.json (e.g. "g", "tbsp", "clove"). */
export type UnitId = string

export type UnitCategoryDefinition = {
  id: UnitCategory
  label: string
}

export type UnitDefinition = {
  id: UnitId
  label: string
  shortLabel: string
  category: UnitCategory
  /** False for non-numeric units ("to-taste", "as-needed"), which carry no quantity. */
  quantifiable: boolean
  aliases: readonly string[]
}

export const UNIT_CATEGORIES: readonly UnitCategoryDefinition[] = stepCatalogsData.unitCategories

export const UNIT_CATEGORY_ORDER: readonly UnitCategory[] = UNIT_CATEGORIES.map((category) => category.id)

export const UNIT_CATALOG: readonly UnitDefinition[] = stepCatalogsData.units

export const CUSTOM_UNIT_ID: UnitId = 'custom'
/** The plain count unit — shown as a bare number ("2 onions"), and the fallback when an ingredient has no default. */
export const PIECE_UNIT_ID: UnitId = 'piece'

const unitById = new Map<UnitId, UnitDefinition>(UNIT_CATALOG.map((unit) => [unit.id, unit]))

const categoryLabelById = new Map<UnitCategory, string>(UNIT_CATEGORIES.map((category) => [category.id, category.label]))

// Aliases also cover the Process model's legacy UnitType values (COUNT/GRAM/KG/ML/LITER), so
// steps saved before the unit catalog existed resolve onto catalog units on read.
const unitAliasLookup = buildAliasLookup(
  UNIT_CATALOG.map((unit) => ({ id: unit.id, aliases: [unit.id, unit.label, unit.shortLabel, ...unit.aliases] }))
)

export const UNITS_BY_CATEGORY: Readonly<Record<UnitCategory, readonly UnitDefinition[]>> =
  UNIT_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = UNIT_CATALOG.filter((unit) => unit.category === category)
    return accumulator
  }, {} as Record<UnitCategory, readonly UnitDefinition[]>)

export const getUnitCategoryLabel = (category: UnitCategory) => categoryLabelById.get(category) ?? category

export const isUnitId = (value: unknown): value is UnitId =>
  typeof value === 'string' && unitById.has(value)

const UNKNOWN_UNIT: UnitDefinition = { id: CUSTOM_UNIT_ID, label: 'Unknown unit', shortLabel: '', category: 'other', quantifiable: true, aliases: [] }

export const getUnitById = (unitId: UnitId): UnitDefinition =>
  unitById.get(unitId) ?? UNKNOWN_UNIT

export const resolveUnitId = (value: unknown): UnitId | '' => resolveCatalogId(unitAliasLookup, value)

export const isQuantifiableUnit = (unitId: UnitId | '') => !unitId || getUnitById(unitId).quantifiable

export const getUnitDisplayValue = (unitId: UnitId | '', customUnit = '') =>
  getCatalogDisplayName(CUSTOM_UNIT_ID, unitId, customUnit, (id) => getUnitById(id).shortLabel, '')

/** "200 g", "2 tbsp", "2" (a plain piece count), "to taste" (non-numeric), or "" when there's nothing to show. */
export const formatQuantityWithUnit = (quantity: number | null | undefined, unitId: UnitId | '') => {
  if (unitId && !isQuantifiableUnit(unitId)) return getUnitById(unitId).shortLabel
  if (quantity == null) return ''
  const unitLabel = unitId && unitId !== PIECE_UNIT_ID && unitId !== CUSTOM_UNIT_ID ? getUnitById(unitId).shortLabel : ''
  return unitLabel ? `${quantity} ${unitLabel}` : String(quantity)
}
