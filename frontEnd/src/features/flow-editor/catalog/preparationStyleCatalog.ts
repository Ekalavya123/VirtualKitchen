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

const styleById = new Map<PreparationStyleId, PreparationStyleDefinition>(
  PREPARATION_STYLE_CATALOG.map((style) => [style.id, style])
)

const styleAliasLookup = buildAliasLookup(
  PREPARATION_STYLE_CATALOG.map((style) => ({ id: style.id, aliases: [style.id, style.label] }))
)

export const resolvePreparationStyleId = (value: unknown): PreparationStyleId | '' =>
  resolveCatalogId(styleAliasLookup, value)

export const getPreparationStyleById = (id: PreparationStyleId): PreparationStyleDefinition =>
  styleById.get(id) as PreparationStyleDefinition

export const getPreparationStyleDisplayName = (styleId: PreparationStyleId | '', customStyle = '') =>
  getCatalogDisplayName(CUSTOM_PREPARATION_STYLE_ID, styleId, customStyle, (id) => getPreparationStyleById(id).label, '')
