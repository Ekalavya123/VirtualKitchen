export const DURATION_UNIT_OPTIONS = ['seconds', 'minutes', 'hours'] as const

export type DurationUnitOption = (typeof DURATION_UNIT_OPTIONS)[number]

const normalizeText = (value: string) => value.trim().toLowerCase()

const normalizeDurationUnit = (value: string): DurationUnitOption | '' => {
  const normalized = normalizeText(value)
  if (!normalized) return ''

  if (normalized === 'second' || normalized === 'seconds' || normalized === 'sec' || normalized === 's') return 'seconds'
  if (normalized === 'minute' || normalized === 'minutes' || normalized === 'min' || normalized === 'm') return 'minutes'
  if (normalized === 'hour' || normalized === 'hours' || normalized === 'hr' || normalized === 'h') return 'hours'
  return ''
}

export const buildDurationLabel = (durationValue: string, durationUnit: DurationUnitOption | '') => {
  const value = durationValue.trim()
  if (!value || !durationUnit) return ''
  return `${value} ${durationUnit}`
}

export const parseDurationLabel = (value: string): { durationValue: string; durationUnit: DurationUnitOption | '' } => {
  const text = value.trim()
  if (!text) return { durationValue: '', durationUnit: '' }

  const match = text.match(/^(\d+(?:\.\d+)?)\s*([a-zA-Z]+)/)
  if (!match) return { durationValue: '', durationUnit: '' }

  const parsedUnit = normalizeDurationUnit(match[2])
  if (!parsedUnit) return { durationValue: '', durationUnit: '' }

  return {
    durationValue: match[1],
    durationUnit: parsedUnit,
  }
}
