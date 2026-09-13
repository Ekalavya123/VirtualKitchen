import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

// The precise set of ids is data-driven (see stepCatalogs.data.json), but kept as an explicit
// literal union here so the rest of the app still gets autocomplete/exhaustiveness checking.
export type FlameLevelId = 'low' | 'medium' | 'high' | 'custom'

export type FlameLevelDefinition = {
  id: FlameLevelId
  label: string
}

type RawFlameLevelEntry = {
  id: string
  label: string
}

export const FLAME_LEVEL_CATALOG: readonly FlameLevelDefinition[] = (
  stepCatalogsData.flameLevels as RawFlameLevelEntry[]
).map((entry) => ({
  id: entry.id as FlameLevelId,
  label: entry.label,
}))

export const CUSTOM_FLAME_LEVEL_ID: FlameLevelId = 'custom'

const flameById = new Map<FlameLevelId, FlameLevelDefinition>(FLAME_LEVEL_CATALOG.map((level) => [level.id, level]))

const flameAliasLookup = buildAliasLookup(
  FLAME_LEVEL_CATALOG.map((level) => ({ id: level.id, aliases: [level.id, level.label] }))
)

export const resolveFlameLevelId = (value: unknown): FlameLevelId | '' => resolveCatalogId(flameAliasLookup, value)

export const getFlameLevelById = (id: FlameLevelId): FlameLevelDefinition =>
  flameById.get(id) as FlameLevelDefinition

export const getFlameLevelDisplayName = (levelId: FlameLevelId | '', customLevel = '') =>
  getCatalogDisplayName(CUSTOM_FLAME_LEVEL_ID, levelId, customLevel, (id) => getFlameLevelById(id).label, '')
