// Shared helpers for the "catalog entry, or a custom free-text override" pattern used by
// every step-property catalog (action, ingredient, unit, preparation style, flame level).
// Replaces what used to be 4 near-identical hand-copied implementations.

const normalizeCatalogText = (value: string) => value.trim().toLowerCase().replace(/\s+/g, ' ')

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
