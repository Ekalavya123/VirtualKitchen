import {
  getStepActionById,
  type ActionCategory,
  type StepActionId,
  type StepSchemaFieldKey,
} from './actionCatalog'

export type { StepSchemaFieldKey }

export type StepSchemaFieldConfig = {
  key: StepSchemaFieldKey
  label: string
}

export type StepActionSchema = {
  category: ActionCategory
  amountLabel: string
  unitLabel: string
  fields: readonly StepSchemaFieldConfig[]
}

// Static labels for fields whose label doesn't vary per-action. `quantity`/`unitId` use the
// action's own amountLabel/unitLabel instead (see getStepActionSchema below).
const STATIC_FIELD_LABELS: Partial<Record<StepSchemaFieldKey, string>> = {
  ingredientId: 'Ingredient',
  preparationStyleId: 'Preparation Style',
  temperature: 'Temperature',
  flameLevelId: 'Flame Level',
  duration: 'Duration',
  repeatInterval: 'Repeat Interval',
  notes: 'Notes',
}

const DEFAULT_ACTION_ID: StepActionId = 'add'

export const getStepActionSchema = (action: StepActionId | ''): StepActionSchema => {
  const definition = getStepActionById(action || DEFAULT_ACTION_ID)
  const fields: StepSchemaFieldConfig[] = definition.fields.map((key) => ({
    key,
    label:
      key === 'quantity' ? definition.amountLabel
      : key === 'unitId' ? definition.unitLabel
      : STATIC_FIELD_LABELS[key] ?? key,
  }))

  return {
    category: definition.category,
    amountLabel: definition.amountLabel,
    unitLabel: definition.unitLabel,
    fields,
  }
}

export const isStepFieldEnabled = (action: StepActionId | '', field: StepSchemaFieldKey) =>
  getStepActionSchema(action).fields.some((config) => config.key === field)
