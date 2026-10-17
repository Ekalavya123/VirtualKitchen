import type { Process } from '../../../../../types/process'
import {
  getIngredientById,
  getIngredientCategoryLabel,
  getIngredientCategoryOrder,
  getIngredientsByCategory,
} from '../../../../../shared/catalog/ingredientCatalog'
import { formatQuantityWithUnit } from '../../../../../shared/catalog/unitCatalog'
import { describePreparedReference, getPreparedOutputs } from '../../../model/preparationFlows'
import { getActionOnIngredientDisplayName, type RecipeStepFields } from '../../../model/recipeStepData'
import { getAvailableStepOutputs, getStepOutputLabel, type StepOutputGraph } from '../../../model/recipeStepOutputs'
import { CUSTOM_INGREDIENT_PREFIX, ingredientAnnotationId, preparedAnnotationId } from '../../../model/stepAnnotations'
import type { AnnotationOption } from './AnnotatedTextEditor'

/**
 * What "@" can name in one step, from the recipe's own sources (no separate list): the step's current targets,
 * the ingredient catalog, the Preparation's outputs (main steps only — preparation flows are independent) and
 * the earlier steps' results this step can reach. Each option says what kind it is, and duplicate names carry
 * a hint (category, flow, step number) so no two look the same.
 */

export type StepAnnotationContext = {
  nodeId: string
  step: RecipeStepFields
  recipeProcesses: Process[]
  isPreparation: boolean
  stepOutputGraph: StepOutputGraph
}

const MAX_CATALOG_MATCHES = 12

const matches = (query: string, ...texts: Array<string | undefined>) => {
  if (!query) return true
  const wanted = query.toLowerCase()
  return texts.some((text) => text?.toLowerCase().includes(wanted))
}

export const buildAnnotationOptions = (context: StepAnnotationContext, query: string): AnnotationOption[] => {
  const { nodeId, step, recipeProcesses, isPreparation, stepOutputGraph } = context
  const options: AnnotationOption[] = []

  // 1. What the step already uses.
  step.actionOn.ingredients.forEach((entry, index) => {
    const id = ingredientAnnotationId(entry)
    const name = getActionOnIngredientDisplayName(entry)
    if (!id || !matches(query, name)) return
    options.push({
      key: `own-ingredient-${index}`,
      annotation: { kind: 'ingredient', id, label: id.startsWith(CUSTOM_INGREDIENT_PREFIX) ? name : name.toLowerCase() },
      group: 'In this step',
      title: name,
      hint: formatQuantityWithUnit(entry.quantity, entry.unit, entry.customUnit) || 'no amount',
      icon: getIngredientById(entry.ingredientId).icon,
      // However it's picked (click or keyboard), an ingredient asks for its amount right away.
      activateOnInsert: true,
    })
  })
  step.actionOn.processes.forEach((entry) => {
    const { label, problem } = describePreparedReference(recipeProcesses, entry)
    if (problem || !matches(query, label)) return
    options.push({
      key: `own-prepared-${preparedAnnotationId(entry)}`,
      annotation: { kind: 'prepared', id: preparedAnnotationId(entry), label },
      group: 'In this step',
      title: label,
    })
  })
  step.actionOn.steps.forEach((entry) => {
    const label = getStepOutputLabel(stepOutputGraph, entry.stepId)
    if (!label || !matches(query, label)) return
    options.push({ key: `own-step-${entry.stepId}`, annotation: { kind: 'step', id: entry.stepId, label }, group: 'In this step', title: label })
  })

  // 2. The ingredient catalog (alias-aware).
  if (query) {
    const used = new Set(step.actionOn.ingredients.map((entry) => ingredientAnnotationId(entry).toLowerCase()))
    const byCategory = getIngredientsByCategory()
    const found = getIngredientCategoryOrder()
      .flatMap((category) => byCategory[category] ?? [])
      .filter((item) => !used.has(item.id.toLowerCase()) && matches(query, item.name, ...item.aliases))
    // Names that start with the query first ("on" → Onion before Lemon).
    const wanted = query.toLowerCase()
    found.sort((a, b) => Number(!a.name.toLowerCase().startsWith(wanted)) - Number(!b.name.toLowerCase().startsWith(wanted)))
    found.slice(0, MAX_CATALOG_MATCHES).forEach((item) => options.push({
      key: `ingredient-${item.id}`,
      // Written in lower case, as it reads in a sentence ("Chop the onion"); the id keeps the link.
      annotation: { kind: 'ingredient', id: item.id, label: item.name.toLowerCase() },
      group: 'Ingredients',
      title: item.name,
      hint: getIngredientCategoryLabel(item.category),
      icon: item.icon,
      activateOnInsert: true,
    }))
  }

  // 3. Prepared components (main steps only).
  if (!isPreparation) {
    const usedPrepared = new Set(step.actionOn.processes.map(preparedAnnotationId))
    getPreparedOutputs(recipeProcesses).forEach((output) => {
      const id = preparedAnnotationId(output)
      if (usedPrepared.has(id) || !matches(query, output.label)) return
      options.push({
        key: `prepared-${id}`,
        annotation: { kind: 'prepared', id, label: output.label },
        group: 'Prepared',
        title: output.label,
        hint: `flow ${output.flowNumber}${output.isFlowOutput ? '' : ' · intermediate'}`,
      })
    })
  }

  // 4. Results of earlier steps this step can reach.
  const usedSteps = new Set(step.actionOn.steps.map((entry) => entry.stepId))
  getAvailableStepOutputs(stepOutputGraph, nodeId).forEach((source) => {
    if (usedSteps.has(source.stepId) || !matches(query, source.label)) return
    options.push({
      key: `step-${source.stepId}`,
      annotation: { kind: 'step', id: source.stepId, label: source.label },
      group: 'Earlier results',
      title: source.label,
      hint: `step ${source.stepNumber}`,
    })
  })

  // 5. Last: a new (custom) ingredient for a name nothing above matches exactly.
  const wanted = query.trim().toLowerCase()
  if (wanted && !options.some((option) => option.title.toLowerCase() === wanted)) {
    options.push({
      key: `custom-${wanted}`,
      annotation: { kind: 'ingredient', id: `${CUSTOM_INGREDIENT_PREFIX}${query.trim()}`, label: query.trim() },
      group: 'New ingredient',
      title: `Use “${query.trim()}” as a new ingredient`,
      hint: 'custom',
      icon: '✏️',
      activateOnInsert: true,
    })
  }

  return options
}
