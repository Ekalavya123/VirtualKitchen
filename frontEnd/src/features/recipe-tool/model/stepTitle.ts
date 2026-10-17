/**
 * A step's title, derived — never typed: the action's verb and what it works on, e.g. "Chop onions",
 * "Heat Marinated Chicken", "Mix sauce and spices". What it works on comes from the description's @ annotations
 * (in the order the instruction names them), else from the step's Action On targets. No AI involved: the title
 * follows every change of action, description or targets.
 *
 * Pure (catalog-free; names are passed in) so it runs under node --test.
 */

import { annotationsOf, CUSTOM_INGREDIENT_PREFIX, toPlainText, type Annotation, type AnnotationLabelResolver } from './stepAnnotations.ts'

export type StepTitleInput = {
  /** The action's display name ("Mix / Combine", or a custom action's name); '' when no action is chosen. */
  actionName: string
  /** The raw description (may contain annotations). */
  description: string
  /** What the step works on when the description names nothing: earlier results, ingredients, prepared outputs. */
  subjects?: readonly string[]
  /** Live names of annotated entities (else their snapshot labels are used). */
  resolveLabel?: AnnotationLabelResolver
}

export const UNTITLED_STEP = 'New step'

/** "Mix" for "Mix / Combine" — the catalog's first name reads as a verb. */
export const actionVerbOf = (actionName: string) => actionName.split(' / ')[0].trim()

/** Catalog ingredient names read in lower case after a verb ("Chop onion"); custom and prepared names stay as typed. */
const subjectOf = (annotation: Annotation, resolve?: AnnotationLabelResolver) => {
  const label = ((resolve?.(annotation) || '').trim() || annotation.label).trim()
  const catalogIngredient = annotation.kind === 'ingredient' && !annotation.id.startsWith(CUSTOM_INGREDIENT_PREFIX)
  return catalogIngredient ? label.toLowerCase() : label
}

const unique = (items: readonly string[]) => {
  const seen = new Set<string>()
  return items.map((item) => item.trim()).filter((item) => {
    const key = item.toLowerCase()
    if (!item || seen.has(key)) return false
    seen.add(key)
    return true
  })
}

/** "onions", "sauce and spices", "onions, tomatoes +2". */
export const joinSubjects = (subjects: readonly string[]) => {
  if (subjects.length <= 1) return subjects[0] ?? ''
  if (subjects.length === 2) return `${subjects[0]} and ${subjects[1]}`
  return `${subjects[0]}, ${subjects[1]} +${subjects.length - 2}`
}

/** The first clause of the instruction, short enough for a heading. */
const firstClause = (text: string, max = 48) => {
  const clause = text.trim().split(/(?<=[.!?;])\s|,\s/)[0]?.replace(/[.!?;]$/, '').trim() ?? ''
  return clause.length > max ? `${clause.slice(0, max - 1).trimEnd()}…` : clause
}

export const deriveStepTitle = ({ actionName, description, subjects = [], resolveLabel }: StepTitleInput): string => {
  const verb = actionVerbOf(actionName)
  if (!verb) return firstClause(toPlainText(description, resolveLabel)) || UNTITLED_STEP
  const named = unique(annotationsOf(description).map((annotation) => subjectOf(annotation, resolveLabel)))
  const chosen = named.length > 0 ? named : unique(subjects)
  return chosen.length > 0 ? `${verb} ${joinSubjects(chosen)}` : verb
}
