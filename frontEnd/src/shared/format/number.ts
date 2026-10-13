/** At most two decimals, no trailing zeros: 1.5, 250, 0.33. `missing` when there is no finite value. */
export const formatNumber = (value: number | null | undefined, missing = '—'): string => {
  if (value == null || !Number.isFinite(value)) return missing
  return String(Math.round(value * 100) / 100)
}

