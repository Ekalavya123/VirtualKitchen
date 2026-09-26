/**
 * CONDITION node data model of the Recipe Process canvas (a yes/no check between steps, e.g.
 * "Is the water boiling?"). Stored as free-form JSON inside `ProcessNode.data`.
 */

export type ConditionExpectedResult = 'success' | 'failure'

export type ConditionNodeStructuredFields = {
  question: string
  expectedResult: ConditionExpectedResult
  successLabel: string
  failureLabel: string
  notes: string
}

export type ConditionNodeData = {
  title: string
  condition: ConditionNodeStructuredFields
  description?: string
  yesLabel?: string
  noLabel?: string
  sectionId?: string | null
}

const toStringValue = (value: unknown): string => {
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

const asRecord = (value: unknown): Record<string, unknown> => {
  if (typeof value === 'object' && value != null) return value as Record<string, unknown>
  return {}
}

export const createDefaultConditionFields = (): ConditionNodeStructuredFields => ({
  question: '',
  expectedResult: 'success',
  successLabel: 'Yes',
  failureLabel: 'No',
  notes: '',
})

export const getConditionNodeTitle = (question: string) => question.trim() || 'Condition?'

export const normalizeConditionNodeData = (value: unknown): ConditionNodeData => {
  const raw = asRecord(value)
  const rawCondition = asRecord(raw.condition)

  const question = toStringValue(rawCondition.question || raw.title)
  const expectedRaw = toStringValue(rawCondition.expectedResult).toLowerCase()
  const expectedResult: ConditionExpectedResult = expectedRaw === 'failure' ? 'failure' : 'success'

  const successLabel = toStringValue(rawCondition.successLabel || raw.yesLabel || 'Yes')
  const failureLabel = toStringValue(rawCondition.failureLabel || raw.noLabel || 'No')
  const notes = toStringValue(rawCondition.notes || raw.description)

  return {
    title: getConditionNodeTitle(question),
    condition: {
      question,
      expectedResult,
      successLabel,
      failureLabel,
      notes,
    },
    description: notes,
    yesLabel: successLabel,
    noLabel: failureLabel,
    sectionId: typeof raw.sectionId === 'string' || raw.sectionId === null ? raw.sectionId : null,
  }
}
