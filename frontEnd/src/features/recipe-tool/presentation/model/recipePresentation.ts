/**
 * Derives the human-readable Recipe Process (ingredients, ordered cooking steps, checks) from the
 * recipe's process graphs — the same MAIN/SUBPROCESS data the Recipe Editor edits, read from
 * RecipeSessionContext. Nothing here is stored: it's a pure, render-ready projection, so every
 * editor change (saved or not) shows up the next time it's derived.
 *
 * Graph concepts (nodes, edges, handles, ids) stop at this module; the presentation components
 * only ever see steps, checks, outcomes and sections expressed in cooking terms.
 */

import type { Process, ProcessEdge, ProcessNode } from '../../../../types/process'
import { getActionIcon, getStepActionById } from '../../catalog/actionCatalog'
import { CUSTOM_FLAME_LEVEL_ID, getFlameLevelDisplayName } from '../../catalog/flameLevelCatalog'
import { CUSTOM_INGREDIENT_ID, getIngredientById } from '../../catalog/ingredientCatalog'
import { getPreparationStyleDisplayName } from '../../catalog/preparationStyleCatalog'
import { getTemperatureFieldLabel } from '../../catalog/stepFieldCatalog'
import { formatQuantityWithUnit } from '../../catalog/unitCatalog'
import { normalizeConditionNodeData } from '../../process/model/recipeConditionData'
import {
  getActionOnIngredientDisplayName,
  getRecipeStepTemperatureLabel,
  getRecipeStepTitle,
  normalizeRecipeStepNodeData,
  type ActionOnIngredient,
  type RecipeStepFields,
} from '../../process/model/recipeStepData'

// ---------------------------------------------------------------------------------------------
// Presentation types
// ---------------------------------------------------------------------------------------------

export type RecipeIngredientLine = {
  key: string
  name: string
  icon: string
  /** "200 g", "2", "to taste" — '' when no step states an amount. */
  amount: string
  preparations: string[]
}

export type StepIngredientChip = {
  key: string
  name: string
  icon: string
  amount: string
  preparation: string
}

export type StepDetailBadge = {
  kind: 'heat' | 'temperature' | 'duration' | 'repeat'
  icon: string
  label: string
  /** Longer explanation for a tooltip (e.g. "Oven Temperature"). */
  hint?: string
}

/** Something a step works with besides raw ingredients: an earlier step's result, or a prepared component. */
export type StepUsesChip = {
  key: string
  label: string
  /** "from step 2" / "prepared in Marinate the chicken". */
  source: string
  /** In-page anchor of the section that prepares it, when there is one. */
  anchorId?: string
}

/** Where the cook goes next, only when it isn't simply "the next step". */
export type StepContinuation =
  | { kind: 'jump'; direction: 'forward' | 'back'; targetNumber: number; targetTitle: string }
  | { kind: 'choice'; targetNumbers: number[] }
  | { kind: 'end' }

export type RecipeBranchTag = { answer: string; question: string }

export type RecipeTimelineStep = {
  kind: 'step'
  key: string
  number: number
  title: string
  icon: string
  description: string
  expectedOutput: string
  imageUrl?: string
  ingredients: StepIngredientChip[]
  details: StepDetailBadge[]
  uses: StepUsesChip[]
  branch?: RecipeBranchTag
  continuation?: StepContinuation
}

export type CheckOutcome = {
  key: string
  tone: 'yes' | 'no'
  answer: string
  /** 'next' = carry on in order, 'back' = repeat earlier steps, 'forward' = skip ahead. */
  direction: 'next' | 'back' | 'forward'
  targetNumber: number
  targetTitle: string
}

export type RecipeTimelineCheck = {
  kind: 'check'
  key: string
  number: number
  question: string
  notes: string
  outcomes: CheckOutcome[]
  branch?: RecipeBranchTag
}

export type RecipeTimelineItem = RecipeTimelineStep | RecipeTimelineCheck

export type RecipeTimelineSection = {
  key: string
  anchorId: string
  title: string
  isMain: boolean
  items: RecipeTimelineItem[]
}

export type RecipePresentation = {
  sections: RecipeTimelineSection[]
  ingredients: RecipeIngredientLine[]
  stepCount: number
  /** Sum of every timed step, in minutes — undefined when no step has a duration. */
  totalMinutes?: number
  heroImageUrl?: string
}

// ---------------------------------------------------------------------------------------------
// Graph ordering
// ---------------------------------------------------------------------------------------------

const YES_HANDLE = 'condition-yes'
const NO_HANDLE = 'condition-no'

type OrderedProcess = {
  nodes: ProcessNode[]
  /** Edges that loop back to an earlier node (e.g. "not cooked yet → cook again"). */
  backEdges: Set<ProcessEdge>
  outgoing: Map<string, ProcessEdge[]>
  incoming: Map<string, ProcessEdge[]>
}

/**
 * Orders a process's nodes into reading order: dependencies first (a topological order of the graph
 * with loop-back edges removed), each branch kept together rather than interleaved, and canvas
 * position (top-to-bottom, left-to-right) breaking ties so the reader matches how the author laid
 * the flow out.
 */
const orderProcessNodes = (process: Process): OrderedProcess => {
  const rank = new Map<string, number>()
  const byPosition = process.nodes
    .map((node, index) => ({ node, index }))
    .sort((a, b) =>
      (a.node.position?.y ?? 0) - (b.node.position?.y ?? 0)
      || (a.node.position?.x ?? 0) - (b.node.position?.x ?? 0)
      || a.index - b.index)
  byPosition.forEach(({ node }, index) => rank.set(node.id, index))

  const nodeById = new Map(process.nodes.map((node) => [node.id, node]))
  const outgoing = new Map<string, ProcessEdge[]>()
  const incoming = new Map<string, ProcessEdge[]>()
  for (const edge of process.edges) {
    if (!nodeById.has(edge.source) || !nodeById.has(edge.target)) continue
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge])
    incoming.set(edge.target, [...(incoming.get(edge.target) ?? []), edge])
  }

  // A check's "yes" path reads first, then "no"; anything else follows canvas position.
  const handlePriority = (edge: ProcessEdge) => (edge.sourceHandle === YES_HANDLE ? 0 : edge.sourceHandle === NO_HANDLE ? 1 : 2)
  const childEdges = (nodeId: string) =>
    [...(outgoing.get(nodeId) ?? [])].sort((a, b) =>
      handlePriority(a) - handlePriority(b) || (rank.get(a.target) ?? 0) - (rank.get(b.target) ?? 0))

  const sortedIds = byPosition.map(({ node }) => node.id)

  // Pass 1: find loop-back edges with a DFS from the flow's starting points (nodes nothing leads
  // into), then from anything left over (e.g. a loop with no clear entry).
  const backEdges = new Set<ProcessEdge>()
  const state = new Map<string, 'active' | 'done'>()
  const classify = (nodeId: string) => {
    state.set(nodeId, 'active')
    for (const edge of childEdges(nodeId)) {
      const targetState = state.get(edge.target)
      if (targetState === 'active') backEdges.add(edge)
      else if (targetState == null) classify(edge.target)
    }
    state.set(nodeId, 'done')
  }
  const roots = sortedIds.filter((id) => (incoming.get(id) ?? []).length === 0)
  for (const id of [...roots, ...sortedIds]) if (!state.has(id)) classify(id)

  // Pass 2: reverse post-order over the remaining (acyclic) graph. Children and starting points are
  // visited in reverse of their desired order, so reversing the post-order puts them back in order
  // with each branch's steps contiguous.
  const forwardChildren = (nodeId: string) => childEdges(nodeId).filter((edge) => !backEdges.has(edge))
  const visited = new Set<string>()
  const postOrder: string[] = []
  const visit = (nodeId: string) => {
    visited.add(nodeId)
    for (const edge of [...forwardChildren(nodeId)].reverse()) if (!visited.has(edge.target)) visit(edge.target)
    postOrder.push(nodeId)
  }
  const sources = sortedIds.filter((id) => (incoming.get(id) ?? []).every((edge) => backEdges.has(edge)))
  for (const id of [...sources].reverse()) if (!visited.has(id)) visit(id)
  for (const id of [...sortedIds].reverse()) if (!visited.has(id)) visit(id)

  return {
    nodes: postOrder.reverse().map((id) => nodeById.get(id) as ProcessNode),
    backEdges,
    outgoing,
    incoming,
  }
}

/** A process's nodes in reading order (see orderProcessNodes) — also what AI edits number and describe. */
export const getProcessReadingOrder = (process: Process): ProcessNode[] => orderProcessNodes(process).nodes

/** Subprocesses a process's steps use, in the order its (ordered) steps first use them. */
const referencedSubprocessIds = (ordered: ProcessNode[]): number[] => {
  const ids: number[] = []
  for (const node of ordered) {
    if (node.kind !== 'STEP') continue
    for (const entry of normalizeRecipeStepNodeData(node.data).step.actionOn.processes) {
      if (!ids.includes(entry.processId)) ids.push(entry.processId)
    }
  }
  return ids
}

/**
 * Sections in cooking order: whatever a process uses is prepared before it (depth-first), so the
 * MAIN process — which brings everything together — reads last. Subprocesses nothing references
 * still appear (before MAIN), since they're part of the recipe's data.
 */
const orderProcesses = (processes: Process[], orderedById: Map<number, OrderedProcess>): Process[] => {
  const byId = new Map(processes.map((process) => [process.id, process]))
  const result: Process[] = []
  const seen = new Set<number>()
  const visit = (process: Process) => {
    if (seen.has(process.id)) return
    seen.add(process.id)
    for (const id of referencedSubprocessIds(orderedById.get(process.id)?.nodes ?? [])) {
      const sub = byId.get(id)
      if (sub) visit(sub)
    }
    result.push(process)
  }

  const main = processes.find((process) => process.type === 'MAIN')
  if (!main) {
    processes.forEach(visit)
    return result
  }

  // MAIN's own dependencies in the order it uses them, then anything unreferenced, then MAIN.
  visit(main)
  const mainChain = result.splice(0, result.length)
  processes.forEach(visit)
  return [...mainChain.slice(0, -1), ...result, main]
}

// ---------------------------------------------------------------------------------------------
// Wording
// ---------------------------------------------------------------------------------------------

const joinNatural = (parts: string[]) => {
  if (parts.length <= 1) return parts.join('')
  return `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`
}

const withArticle = (text: string) => (/^(the|a|an|some|any|all|\d)/i.test(text) ? text : `the ${text}`)

const firstSentence = (text: string, max = 64) => {
  const sentence = text.trim().split(/(?<=[.!?])\s/)[0] ?? ''
  return sentence.length > max ? `${sentence.slice(0, max - 1).trimEnd()}…` : sentence.replace(/[.]$/, '')
}

/** Ingredient names read in lower case inside a sentence ("Chop the onion"); custom names are kept as typed. */
const ingredientNameInSentence = (entry: ActionOnIngredient) =>
  entry.ingredientId === CUSTOM_INGREDIENT_ID ? getActionOnIngredientDisplayName(entry) : getActionOnIngredientDisplayName(entry).toLowerCase()

const shortList = (items: string[]) => joinNatural(items.length > 3 ? [...items.slice(0, 2), `${items.length - 2} more`] : items)

/**
 * "Fry the chopped onions with cooking oil": what the step works on (earlier results, prepared
 * components) is the object; its own raw ingredients follow "with". A step on raw ingredients
 * alone takes them as the object ("Chop the onion and tomato").
 */
const buildStepTitle = (step: RecipeStepFields, products: string[], ingredients: string[]) => {
  if (!step.action) return step.actionDescription.trim() ? firstSentence(step.actionDescription) : 'Next step'
  const action = getRecipeStepTitle(step)
  if (products.length > 0) {
    return `${action} ${withArticle(shortList(products))}${ingredients.length > 0 ? ` with ${shortList(ingredients)}` : ''}`
  }
  return ingredients.length > 0 ? `${action} ${withArticle(shortList(ingredients))}` : action
}

/** What a step produces, in words: its Expected Output, else what it worked on. */
const stepProductLabel = (node: ProcessNode | undefined) => {
  if (node?.kind !== 'STEP') return ''
  const { step } = normalizeRecipeStepNodeData(node.data)
  return step.expectedOutput.trim() || joinNatural(step.actionOn.ingredients.map(ingredientNameInSentence))
}

/** What a subprocess produces: its last step's Expected Output, else the subprocess's own name. */
const processProductLabel = (process: Process, ordered: OrderedProcess | undefined) => {
  const last = [...(ordered?.nodes ?? [])].reverse().find((node) => node.kind === 'STEP' && normalizeRecipeStepNodeData(node.data).step.expectedOutput.trim())
  return (last ? stepProductLabel(last) : '') || process.name.trim()
}

const SHORT_DURATION_UNITS: Record<string, string> = { seconds: 'sec', minutes: 'min', hours: 'hr' }
const MINUTES_PER_UNIT: Record<string, number> = { seconds: 1 / 60, minutes: 1, hours: 60 }

const durationLabel = (value: string, unit: string) => {
  const trimmed = value.trim()
  if (!trimmed || !unit) return ''
  return `${trimmed} ${SHORT_DURATION_UNITS[unit] ?? unit}`
}

const durationMinutes = (step: RecipeStepFields) => {
  const value = Number(step.durationValue)
  const factor = MINUTES_PER_UNIT[step.durationUnit]
  return Number.isFinite(value) && value > 0 && factor ? value * factor : 0
}

const heatLabel = (step: RecipeStepFields) => {
  const label = getFlameLevelDisplayName(step.flameLevelId, step.customFlameLevel)
  if (!label) return ''
  if (step.flameLevelId === CUSTOM_FLAME_LEVEL_ID || /heat|flame/i.test(label)) return label
  if (step.flameLevelId === 'off') return 'Heat off'
  return `${label} heat`
}

const buildDetailBadges = (step: RecipeStepFields): StepDetailBadge[] => {
  const badges: StepDetailBadge[] = []
  const heat = heatLabel(step)
  if (heat) badges.push({ kind: 'heat', icon: '🔥', label: heat })

  const temperature = getRecipeStepTemperatureLabel(step)
  if (temperature) {
    const context = step.action ? getStepActionById(step.action).temperatureContext : null
    const hint = getTemperatureFieldLabel(context)
    const place = context ? hint.replace(/\s*Temperature$/i, '') : ''
    badges.push({ kind: 'temperature', icon: '🌡️', label: place ? `${place} ${temperature}` : temperature, hint })
  }

  const duration = durationLabel(step.durationValue, step.durationUnit)
  if (duration) badges.push({ kind: 'duration', icon: '⏱️', label: duration })

  const repeat = durationLabel(step.repeatIntervalValue, step.repeatIntervalUnit)
  if (repeat) badges.push({ kind: 'repeat', icon: '🔁', label: `Every ${repeat}` })

  return badges
}

const buildIngredientChip = (entry: ActionOnIngredient, index: number): StepIngredientChip => ({
  key: `${entry.ingredientId}-${entry.customIngredientName ?? ''}-${index}`,
  name: getActionOnIngredientDisplayName(entry),
  icon: getIngredientById(entry.ingredientId).icon,
  amount: formatQuantityWithUnit(entry.quantity, entry.unit),
  preparation: getPreparationStyleDisplayName(entry.preparationStyleId ?? '', entry.customPreparationStyle),
})

const GENERIC_MAIN_NAME = /^\s*main(\s+process)?\s*$/i

export const sectionAnchorId = (processId: number) => `recipe-part-${processId}`

// ---------------------------------------------------------------------------------------------
// Builder
// ---------------------------------------------------------------------------------------------

/**
 * Builds the complete Recipe Process presentation from the recipe's processes. Step numbers run
 * continuously through the whole recipe, so a check can point back to "step 3" regardless of
 * which part of the recipe it's in.
 */
export const buildRecipePresentation = (processes: Process[]): RecipePresentation => {
  const orderedById = new Map(processes.map((process) => [process.id, orderProcessNodes(process)]))
  const processById = new Map(processes.map((process) => [process.id, process]))
  const sectionProcesses = orderProcesses(processes, orderedById).filter((process) => process.nodes.length > 0)
  const productOf = (processId: number) => {
    const process = processById.get(processId)
    return process ? processProductLabel(process, orderedById.get(processId)) : ''
  }

  // Numbering first, so references (checks, jumps, "from step N") can resolve any item.
  const numberOf = new Map<string, number>()
  let counter = 0
  for (const process of sectionProcesses) {
    for (const node of orderedById.get(process.id)?.nodes ?? []) numberOf.set(`${process.id}:${node.id}`, ++counter)
  }

  // Summed across every step, grouped by ingredient + unit; a step that mentions an ingredient
  // without an amount ("stir the onions") adds nothing to its total.
  const ingredientTotals = new Map<string, Omit<RecipeIngredientLine, 'amount'> & { quantity: number | null; unit: ActionOnIngredient['unit'] }>()
  let totalMinutes = 0
  let heroImageUrl: string | undefined
  let fallbackImageUrl: string | undefined

  const sections = sectionProcesses.map<RecipeTimelineSection>((process) => {
    const ordered = orderedById.get(process.id) as OrderedProcess
    const stepById = new Map(ordered.nodes.map((node) => [node.id, node]))
    const titles = new Map<string, string>()
    const numberFor = (nodeId: string) => numberOf.get(`${process.id}:${nodeId}`) ?? 0
    const indexOf = new Map(ordered.nodes.map((node, index) => [node.id, index]))

    // Titles first too — outcomes and jumps quote their target's title.
    const stepFields = new Map<string, RecipeStepFields>()
    for (const node of ordered.nodes) {
      if (node.kind === 'CONDITION') {
        titles.set(node.id, normalizeConditionNodeData(node.data).condition.question.trim() || 'Check before continuing')
        continue
      }
      const { step } = normalizeRecipeStepNodeData(node.data)
      stepFields.set(node.id, step)
      const products = [
        ...step.actionOn.steps.map((entry) => stepProductLabel(stepById.get(entry.stepId))),
        ...step.actionOn.processes.map((entry) => productOf(entry.processId)),
      ].filter(Boolean)
      titles.set(node.id, buildStepTitle(step, products, step.actionOn.ingredients.map(ingredientNameInSentence)))
    }

    const relativeDirection = (fromId: string, toId: string): CheckOutcome['direction'] => {
      const from = indexOf.get(fromId) ?? 0
      const to = indexOf.get(toId) ?? 0
      if (to === from + 1) return 'next'
      return to <= from ? 'back' : 'forward'
    }

    /** "Only if …" when the only way into this item is one side of a genuine fork (not a retry loop). */
    const branchTagFor = (nodeId: string): RecipeBranchTag | undefined => {
      const into = ordered.incoming.get(nodeId) ?? []
      if (into.length !== 1) return undefined
      const edge = into[0]
      if (edge.sourceHandle !== YES_HANDLE && edge.sourceHandle !== NO_HANDLE) return undefined
      const condition = stepById.get(edge.source)
      if (condition?.kind !== 'CONDITION') return undefined
      const otherSide = (ordered.outgoing.get(edge.source) ?? []).find((other) =>
        other.sourceHandle !== edge.sourceHandle && (other.sourceHandle === YES_HANDLE || other.sourceHandle === NO_HANDLE))
      if (!otherSide || ordered.backEdges.has(otherSide)) return undefined
      const { condition: fields } = normalizeConditionNodeData(condition.data)
      const answer = edge.label?.trim() || (edge.sourceHandle === YES_HANDLE ? fields.successLabel : fields.failureLabel)
      return { answer, question: fields.question.trim() }
    }

    const items = ordered.nodes.map<RecipeTimelineItem>((node, index) => {
      const number = numberFor(node.id)
      const key = `${process.id}:${node.id}`

      if (node.kind === 'CONDITION') {
        const { condition } = normalizeConditionNodeData(node.data)
        const outcomes: CheckOutcome[] = []
        for (const [handle, tone, fallback] of [
          [YES_HANDLE, 'yes', condition.successLabel],
          [NO_HANDLE, 'no', condition.failureLabel],
        ] as const) {
          const edge = (ordered.outgoing.get(node.id) ?? []).find((candidate) => candidate.sourceHandle === handle)
          if (!edge) continue
          outcomes.push({
            key: `${key}:${handle}`,
            tone,
            answer: edge.label?.trim() || fallback || (tone === 'yes' ? 'Yes' : 'No'),
            direction: relativeDirection(node.id, edge.target),
            targetNumber: numberFor(edge.target),
            targetTitle: titles.get(edge.target) ?? '',
          })
        }
        return {
          kind: 'check',
          key,
          number,
          question: titles.get(node.id) ?? '',
          notes: condition.notes.trim(),
          outcomes,
          branch: branchTagFor(node.id),
        }
      }

      const step = stepFields.get(node.id) as RecipeStepFields
      const { visualization } = normalizeRecipeStepNodeData(node.data)

      for (const entry of step.actionOn.ingredients) {
        const ingredientKey = `${entry.ingredientId}:${entry.customIngredientName ?? ''}:${entry.unit}`
        const preparation = getPreparationStyleDisplayName(entry.preparationStyleId ?? '', entry.customPreparationStyle)
        const existing = ingredientTotals.get(ingredientKey)
        if (existing) {
          if (entry.quantity != null) existing.quantity = (existing.quantity ?? 0) + entry.quantity
          if (preparation && !existing.preparations.includes(preparation)) existing.preparations.push(preparation)
        } else {
          ingredientTotals.set(ingredientKey, {
            key: ingredientKey,
            name: getActionOnIngredientDisplayName(entry),
            icon: getIngredientById(entry.ingredientId).icon,
            preparations: preparation ? [preparation] : [],
            quantity: entry.quantity,
            unit: entry.unit,
          })
        }
      }

      totalMinutes += durationMinutes(step)
      if (visualization?.imageUrl) {
        fallbackImageUrl ??= visualization.imageUrl
        // The last illustrated step of the main process is usually the finished dish.
        if (process.type === 'MAIN') heroImageUrl = visualization.imageUrl
      }

      const uses: StepUsesChip[] = [
        ...step.actionOn.steps.flatMap<StepUsesChip>((entry) => {
          const label = stepProductLabel(stepById.get(entry.stepId))
          return label ? [{ key: `step-${entry.stepId}`, label, source: `from step ${numberFor(entry.stepId)}` }] : []
        }),
        ...step.actionOn.processes.flatMap<StepUsesChip>((entry) => {
          const sub = processById.get(entry.processId)
          if (!sub) return []
          const shown = sectionProcesses.includes(sub)
          const name = sub.name.trim()
          return [{
            key: `process-${entry.processId}`,
            label: productOf(sub.id) || 'Prepared component',
            source: name ? `from “${name}”` : 'prepared separately',
            anchorId: shown ? sectionAnchorId(sub.id) : undefined,
          }]
        }),
      ]

      // Only mention where to go next when it isn't simply the following item.
      const next = ordered.outgoing.get(node.id) ?? []
      let continuation: StepContinuation | undefined
      if (next.length === 0) {
        if (index < ordered.nodes.length - 1) continuation = { kind: 'end' }
      } else if (next.length === 1) {
        const direction = relativeDirection(node.id, next[0].target)
        if (direction !== 'next') {
          continuation = {
            kind: 'jump',
            direction,
            targetNumber: numberFor(next[0].target),
            targetTitle: titles.get(next[0].target) ?? '',
          }
        }
      } else {
        continuation = { kind: 'choice', targetNumbers: [...new Set(next.map((edge) => numberFor(edge.target)))].sort((a, b) => a - b) }
      }

      return {
        kind: 'step',
        key,
        number,
        title: titles.get(node.id) ?? '',
        icon: step.action ? getActionIcon(step.action) : '🍳',
        description: step.actionDescription.trim(),
        expectedOutput: step.expectedOutput.trim(),
        imageUrl: visualization?.imageUrl,
        ingredients: step.actionOn.ingredients.map(buildIngredientChip),
        details: buildDetailBadges(step),
        uses,
        branch: branchTagFor(node.id),
        continuation,
      }
    })

    const isMain = process.type === 'MAIN'
    const name = process.name.trim()
    return {
      key: String(process.id),
      anchorId: sectionAnchorId(process.id),
      title: isMain && (!name || GENERIC_MAIN_NAME.test(name)) ? 'Bring it all together' : name || 'Preparation',
      isMain,
      items,
    }
  })

  const ingredients = Array.from(ingredientTotals.values()).map<RecipeIngredientLine>(({ quantity, unit, key, name, icon, preparations }) => ({
    key,
    name,
    icon,
    amount: formatQuantityWithUnit(quantity, unit),
    preparations,
  }))

  return {
    sections,
    ingredients,
    stepCount: sections.reduce((count, section) => count + section.items.filter((item) => item.kind === 'step').length, 0),
    totalMinutes: totalMinutes > 0 ? Math.round(totalMinutes) : undefined,
    heroImageUrl: heroImageUrl ?? fallbackImageUrl,
  }
}

/** "1 hr 15 min", "40 min". */
export const formatTotalMinutes = (minutes: number) => {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} hr ${rest} min` : `${hours} hr`
}
