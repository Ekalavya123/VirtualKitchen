// Shared helpers for the "catalog entry, or a custom free-text override" pattern used by
// every step-property catalog (action, ingredient, unit, preparation style, flame level).
// Replaces what used to be 4 near-identical hand-copied implementations.

export const normalizeCatalogText = (value: string) => value.trim().toLowerCase().replace(/\s+/g, ' ')

export function buildAliasLookup<T extends string>(
  entries: readonly { id: T; aliases: readonly string[] }[]
): Map<string, T> {
  const map = new Map<string, T>()
  for (const entry of entries) {
    for (const alias of entry.aliases) {
      const normalized = normalizeCatalogText(alias)
      if (normalized) map.set(normalized, entry.id)
    }
  }
  return map
}

export function resolveCatalogId<T extends string>(aliasLookup: Map<string, T>, value: unknown): T | '' {
  if (typeof value !== 'string') return ''
  const normalized = normalizeCatalogText(value)
  if (!normalized) return ''
  return aliasLookup.get(normalized) ?? ''
}

export type CatalogInputResolution<T extends string> = {
  id: T | ''
  customValue: string
}

/**
 * Resolves free-form text (typed by a user, or produced by AI generation) against a catalog's
 * alias lookup. A match returns that entry's id; no match falls back to the catalog's `customId`
 * with the raw text preserved as the custom value, instead of being dropped.
 */
export function resolveCatalogInput<T extends string>(
  aliasLookup: Map<string, T>,
  customId: T,
  value: string
): CatalogInputResolution<T> {
  const normalized = normalizeCatalogText(value)
  if (!normalized) return { id: '', customValue: '' }

  const match = aliasLookup.get(normalized)
  if (match) {
    return match === customId ? { id: customId, customValue: '' } : { id: match, customValue: '' }
  }

  return { id: customId, customValue: value.trim() }
}

export function getCatalogDisplayName<T extends string>(
  customId: T,
  id: T | '',
  customValue: string,
  resolveLabel: (id: T) => string,
  customFallbackLabel: string
): string {
  if (!id) return ''
  if (id === customId) return customValue.trim() || customFallbackLabel
  return resolveLabel(id)
}
