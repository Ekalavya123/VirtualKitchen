import stepCatalogsData from './stepCatalogs.data.json'
import {
  buildAliasLookup,
  getCatalogDisplayName,
  resolveCatalogId,
} from './catalogSelectionUtils'

/** A preparation style id from stepCatalogs.data.json (e.g. "finely-chopped"). */
export type PreparationStyleId = string

export type PreparationStyleDefinition = {
  id: PreparationStyleId
  label: string
  aliases: readonly string[]
}

/** A named group of styles; actions and ingredient categories each list the sets they can use. */
export type PreparationStyleSet = {
  id: string
  label: string
  styles: readonly PreparationStyleId[]
}

export const PREPARATION_STYLE_CATALOG: readonly PreparationStyleDefinition[] = stepCatalogsData.preparationStyles

export const PREPARATION_STYLE_SETS: readonly PreparationStyleSet[] = stepCatalogsData.preparationStyleSets

export const CUSTOM_PREPARATION_STYLE_ID: PreparationStyleId = 'custom'
/** Pre-fill for a newly added Action On ingredient — only used when it's actually allowed for that action/ingredient (see actionSchemaCatalog's `getAllowedPreparationStyles`). */
export const DEFAULT_PREPARATION_STYLE_ID: PreparationStyleId = 'medium'

const styleById = new Map<PreparationStyleId, PreparationStyleDefinition>(
  PREPARATION_STYLE_CATALOG.map((style) => [style.id, style])
)

const setById = new Map<string, PreparationStyleSet>(PREPARATION_STYLE_SETS.map((set) => [set.id, set]))

const styleAliasLookup = buildAliasLookup(
  PREPARATION_STYLE_CATALOG.map((style) => ({ id: style.id, aliases: [style.id, style.label, ...style.aliases] }))
)

/** Every style in the given sets, in catalog order, without duplicates. */
export const getStylesOfSets = (setIds: readonly string[]): PreparationStyleId[] => {
  const wanted = new Set(setIds.flatMap((setId) => setById.get(setId)?.styles ?? []))
  return PREPARATION_STYLE_CATALOG.map((style) => style.id).filter((id) => wanted.has(id))
}

export const resolvePreparationStyleId = (value: unknown): PreparationStyleId | '' =>
  resolveCatalogId(styleAliasLookup, value)

// Falls back instead of crashing when `id` doesn't resolve (e.g. stale/older saved data) — this is
// looked up during render (canvas node labels, the Action On panel), so it must never throw.
const UNKNOWN_PREPARATION_STYLE: PreparationStyleDefinition = { id: CUSTOM_PREPARATION_STYLE_ID, label: 'Unknown', aliases: [] }

export const getPreparationStyleById = (id: PreparationStyleId): PreparationStyleDefinition =>
  styleById.get(id) ?? UNKNOWN_PREPARATION_STYLE

export const getPreparationStyleDisplayName = (styleId: PreparationStyleId | '', customStyle = '') =>
  getCatalogDisplayName(CUSTOM_PREPARATION_STYLE_ID, styleId, customStyle, (id) => getPreparationStyleById(id).label, '')
