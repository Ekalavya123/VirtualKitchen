import stepCatalogsData from './stepCatalogs.data.json'
import { buildAliasLookup, resolveCatalogId } from './catalogSelectionUtils'

// --- duration / repeat interval ("5 minutes") ---

/** "seconds" | "minutes" | "hours" — from the catalog's `durationUnits`. */
export type DurationUnitOption = string

export const DURATION_UNIT_OPTIONS: readonly DurationUnitOption[] = stepCatalogsData.durationUnits.map((unit) => unit.id)

const durationUnitLookup = buildAliasLookup(
  stepCatalogsData.durationUnits.map((unit) => ({ id: unit.id, aliases: [unit.id, unit.label, ...unit.aliases] }))
)

export const isDurationUnitOption = (value: unknown): value is DurationUnitOption =>
  typeof value === 'string' && DURATION_UNIT_OPTIONS.includes(value)

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

  const parsedUnit = resolveCatalogId(durationUnitLookup, match[2])
  if (!parsedUnit) return { durationValue: '', durationUnit: '' }

  return {
    durationValue: match[1],
    durationUnit: parsedUnit,
  }
}

// --- temperature (numeric value + C/F; what it measures comes from the action's temperatureContext) ---

/** "C" | "F" — from the catalog's `temperatureUnits`. */
export type TemperatureUnitOption = string

export const TEMPERATURE_UNIT_OPTIONS: readonly { id: TemperatureUnitOption; label: string }[] = stepCatalogsData.temperatureUnits

const temperatureUnitLookup = buildAliasLookup(
  stepCatalogsData.temperatureUnits.map((unit) => ({ id: unit.id, aliases: [unit.id, unit.label, ...unit.aliases] }))
)

const TEMPERATURE_CONTEXT_LABELS = new Map<string, string>(
  stepCatalogsData.temperatureContexts.map((context) => [context.id, context.label])
)

export const resolveTemperatureUnit = (value: unknown): TemperatureUnitOption | '' => resolveCatalogId(temperatureUnitLookup, value)

/** Field label for an action's temperature, e.g. "Oven Temperature" (just "Temperature" when the action has no context). */
export const getTemperatureFieldLabel = (temperatureContext: string | null) =>
  (temperatureContext && TEMPERATURE_CONTEXT_LABELS.get(temperatureContext)) || 'Temperature'

export const buildTemperatureLabel = (temperatureValue: string, temperatureUnit: TemperatureUnitOption | '') => {
  const value = temperatureValue.trim()
  if (!value) return ''
  const unitLabel = TEMPERATURE_UNIT_OPTIONS.find((unit) => unit.id === temperatureUnit)?.label
  return unitLabel ? `${value} ${unitLabel}` : value
}

/**
 * Parses a legacy free-text temperature ("180 C", "350°F") into value + unit. Text that isn't a
 * number + unit is kept whole as the value (with no unit), so nothing a user typed is lost.
 */
export const parseTemperatureLabel = (value: string): { temperatureValue: string; temperatureUnit: TemperatureUnitOption | '' } => {
  const text = value.trim()
  if (!text) return { temperatureValue: '', temperatureUnit: '' }

  const match = text.match(/^(-?\d+(?:\.\d+)?)\s*(°?\s*[a-zA-Z]+)?$/)
  if (!match) return { temperatureValue: text, temperatureUnit: '' }

  const unit = match[2] ? resolveTemperatureUnit(match[2].replace(/\s+/g, '')) : ''
  return unit || !match[2] ? { temperatureValue: match[1], temperatureUnit: unit } : { temperatureValue: text, temperatureUnit: '' }
}
