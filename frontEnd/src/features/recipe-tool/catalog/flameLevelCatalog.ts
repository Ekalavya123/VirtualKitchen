import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

/**
 * A heat level id from stepCatalogs.data.json ("off" … "very-high"). This is the burner/appliance
 * setting — a numeric temperature is a separate step field (see stepFieldCatalog's temperature helpers).
 */
export type FlameLevelId = string

export type FlameLevelDefinition = {
  id: FlameLevelId
  label: string
  description?: string
}

export const FLAME_LEVEL_CATALOG: readonly FlameLevelDefinition[] = stepCatalogsData.flameLevels

export const CUSTOM_FLAME_LEVEL_ID: FlameLevelId = 'custom'

const flameById = new Map<FlameLevelId, FlameLevelDefinition>(FLAME_LEVEL_CATALOG.map((level) => [level.id, level]))

const flameAliasLookup = buildAliasLookup(
  FLAME_LEVEL_CATALOG.map((level) => ({ id: level.id, aliases: [level.id, level.label] }))
)

export const resolveFlameLevelId = (value: unknown): FlameLevelId | '' => resolveCatalogId(flameAliasLookup, value)

// Falls back instead of crashing when `id` doesn't resolve (e.g. stale/older saved data) — this is
// looked up during render (canvas node detail badges), so it must never throw.
const UNKNOWN_FLAME_LEVEL: FlameLevelDefinition = { id: CUSTOM_FLAME_LEVEL_ID, label: 'Unknown' }

export const getFlameLevelById = (id: FlameLevelId): FlameLevelDefinition =>
  flameById.get(id) ?? UNKNOWN_FLAME_LEVEL

export const getFlameLevelDisplayName = (levelId: FlameLevelId | '', customLevel = '') =>
  getCatalogDisplayName(CUSTOM_FLAME_LEVEL_ID, levelId, customLevel, (id) => getFlameLevelById(id).label, '')
