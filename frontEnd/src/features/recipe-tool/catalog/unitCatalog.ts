import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

export const UNIT_CATEGORY_ORDER = ['Volume', 'Weight', 'Count', 'Spoon', 'Other'] as const

export type UnitCategory = (typeof UNIT_CATEGORY_ORDER)[number]

// The precise set of ids is data-driven (see stepCatalogs.data.json), but kept as an explicit
// literal union here so the rest of the app still gets autocomplete/exhaustiveness checking.
export type UnitId = 'ml' | 'l' | 'cup' | 'g' | 'kg' | 'piece' | 'tsp' | 'tbsp' | 'pinch' | 'custom'

export type UnitDefinition = {
  id: UnitId
  label: string
  shortLabel: string
  category: UnitCategory
}

type RawUnitEntry = {
  id: string
  label: string
  shortLabel: string
  category: string
}

export const UNIT_CATALOG: readonly UnitDefinition[] = (stepCatalogsData.units as RawUnitEntry[]).map((entry) => ({
  id: entry.id as UnitId,
  label: entry.label,
  shortLabel: entry.shortLabel,
  category: entry.category as UnitCategory,
}))

export const CUSTOM_UNIT_ID: UnitId = 'custom'

const unitById = new Map<UnitId, UnitDefinition>(UNIT_CATALOG.map((unit) => [unit.id, unit]))

const unitAliasLookup = buildAliasLookup(
  UNIT_CATALOG.map((unit) => ({ id: unit.id, aliases: [unit.id, unit.label, unit.shortLabel] }))
)

export const UNITS_BY_CATEGORY: Readonly<Record<UnitCategory, readonly UnitDefinition[]>> =
  UNIT_CATEGORY_ORDER.reduce((accumulator, category) => {
    accumulator[category] = UNIT_CATALOG.filter((unit) => unit.category === category)
    return accumulator
  }, {} as Record<UnitCategory, readonly UnitDefinition[]>)

export const isUnitId = (value: unknown): value is UnitId =>
  typeof value === 'string' && unitById.has(value as UnitId)

export const getUnitById = (unitId: UnitId): UnitDefinition =>
  unitById.get(unitId) as UnitDefinition

export const resolveUnitId = (value: unknown): UnitId | '' => resolveCatalogId(unitAliasLookup, value)

export const getUnitDisplayValue = (unitId: UnitId | '', customUnit = '') =>
  getCatalogDisplayName(CUSTOM_UNIT_ID, unitId, customUnit, (id) => getUnitById(id).shortLabel, '')
