import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

// The precise set of ids is data-driven (see stepCatalogs.data.json), but kept as an explicit
// literal union here so the rest of the app still gets autocomplete/exhaustiveness checking.
export type PreparationStyleId =
  | 'fine' | 'medium' | 'large' | 'thin-slice' | 'thick-slice' | 'julienne' | 'rough-chop' | 'custom'

export type PreparationStyleDefinition = {
  id: PreparationStyleId
  label: string
}

type RawPreparationStyleEntry = {
  id: string
  label: string
}

export const PREPARATION_STYLE_CATALOG: readonly PreparationStyleDefinition[] = (
  stepCatalogsData.preparationStyles as RawPreparationStyleEntry[]
).map((entry) => ({
  id: entry.id as PreparationStyleId,
  label: entry.label,
}))

export const CUSTOM_PREPARATION_STYLE_ID: PreparationStyleId = 'custom'
/** Pre-fill for a newly added Action On ingredient, when the current action needs a preparation style at all — see actionSchemaCatalog's `isStepFieldEnabled`. */
export const DEFAULT_PREPARATION_STYLE_ID: PreparationStyleId = 'medium'

const styleById = new Map<PreparationStyleId, PreparationStyleDefinition>(
  PREPARATION_STYLE_CATALOG.map((style) => [style.id, style])
)

const styleAliasLookup = buildAliasLookup(
  PREPARATION_STYLE_CATALOG.map((style) => ({ id: style.id, aliases: [style.id, style.label] }))
)

export const resolvePreparationStyleId = (value: unknown): PreparationStyleId | '' =>
  resolveCatalogId(styleAliasLookup, value)

// Falls back instead of crashing when `id` doesn't resolve (e.g. stale/older saved data) — this is
// looked up during render (canvas node labels, the Action On panel), so it must never throw.
const UNKNOWN_PREPARATION_STYLE: PreparationStyleDefinition = { id: CUSTOM_PREPARATION_STYLE_ID, label: 'Unknown' }

export const getPreparationStyleById = (id: PreparationStyleId): PreparationStyleDefinition =>
  styleById.get(id) ?? UNKNOWN_PREPARATION_STYLE

export const getPreparationStyleDisplayName = (styleId: PreparationStyleId | '', customStyle = '') =>
  getCatalogDisplayName(CUSTOM_PREPARATION_STYLE_ID, styleId, customStyle, (id) => getPreparationStyleById(id).label, '')
