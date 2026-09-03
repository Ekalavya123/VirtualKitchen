import {
  normalizeConditionNodeData,
  normalizeParallelNodeData,
  normalizeStepNodeData,
  type FlowData,
  type FlowEdgePayload,
  type FlowNodePayload,
  type RecipeExecutionModel,
  type RecipeExecutionNodeType,
  type RecipeExecutionStep,
} from '../../../../types/recipeFlow'
import { FLOW_NODE_TYPES } from '../../model/flowNodeModel'

const NODE_SIZES: Record<RecipeExecutionNodeType, { width: number; height: number }> = {
  recipeStep: { width: 320, height: 190 },
  condition: { width: 190, height: 190 },
  parallelStart: { width: 180, height: 92 },
  parallelEnd: { width: 180, height: 92 },
}

const HORIZONTAL_GAP = 380
const VERTICAL_GAP = 260
const ORIGIN_X = 120
const ORIGIN_Y = 80

const VALID_NODE_TYPES: RecipeExecutionNodeType[] = ['recipeStep', 'condition', 'parallelStart', 'parallelEnd']

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null

const toStringValue = (value: unknown) => {
  if (value == null) return ''
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

const toNodeType = (value: unknown): RecipeExecutionNodeType => {
  const candidate = toStringValue(value).trim()
  return VALID_NODE_TYPES.includes(candidate as RecipeExecutionNodeType)
    ? (candidate as RecipeExecutionNodeType)
    : 'recipeStep'
}

const toStringRecord = (value: unknown): Record<string, string> => {
  if (!isRecord(value)) return {}
  return Object.entries(value).reduce<Record<string, string>>((acc, [key, fieldValue]) => {
    acc[key] = toStringValue(fieldValue)
    return acc
  }, {})
}

const buildLegacyStepData = (entry: Record<string, unknown>): Record<string, string> => ({
  action: toStringValue(entry.action),
  ingredientId: toStringValue(entry.ingredientId),
  quantity: toStringValue(entry.quantity),
  unit: toStringValue(entry.unit),
  style: toStringValue(entry.style),
  duration: toStringValue(entry.duration),
  flame: toStringValue(entry.flame),
  temperature: toStringValue(entry.temperature),
  notes: toStringValue(entry.description),
})

const toExecutionStep = (entry: unknown, index: number): RecipeExecutionStep | null => {
  if (!isRecord(entry)) return null

  const nodeType = toNodeType(entry.nodeType)
  const nodeData = isRecord(entry.data) ? toStringRecord(entry.data) : buildLegacyStepData(entry)

  return {
    id: toStringValue(entry.id).trim() || `step-${index + 1}`,
    nodeType,
    data: nodeData,
  }
}

const buildTopologicalOrder = (steps: RecipeExecutionStep[], edges: FlowEdgePayload[]) => {
  const stepIds = steps.map((step) => step.id)
  const indegree = new Map<string, number>(stepIds.map((id) => [id, 0]))
  const adjacency = new Map<string, string[]>()

  edges.forEach((edge) => {
    if (!indegree.has(edge.source) || !indegree.has(edge.target)) return
    indegree.set(edge.target, (indegree.get(edge.target) ?? 0) + 1)
    const current = adjacency.get(edge.source)
    if (current) {
      current.push(edge.target)
    } else {
      adjacency.set(edge.source, [edge.target])
    }
  })

  const queue: string[] = []
  indegree.forEach((value, id) => {
    if (value === 0) queue.push(id)
  })

  const ordered: string[] = []
  while (queue.length > 0) {
    const id = queue.shift() as string
    ordered.push(id)

    const targets = adjacency.get(id) ?? []
    targets.forEach((target) => {
      const next = (indegree.get(target) ?? 0) - 1
      indegree.set(target, next)
      if (next === 0) queue.push(target)
    })
  }

  if (ordered.length < steps.length) {
    stepIds.forEach((id) => {
      if (!ordered.includes(id)) {
        ordered.push(id)
      }
    })
  }

  return ordered
}

const buildRecipeStepNode = (step: RecipeExecutionStep, stepNumber: number, x: number, y: number): FlowNodePayload => {
  const action = toStringValue(step.data.action)
  const normalized = normalizeStepNodeData({
    title: action,
    stepNumber,
    step: {
      action,
      ingredientId: toStringValue(step.data.ingredientId),
      customIngredientName: toStringValue(step.data.ingredientId),
      quantity: toStringValue(step.data.quantity),
      unitId: toStringValue(step.data.unit),
      customUnit: toStringValue(step.data.unit),
      unit: toStringValue(step.data.unit),
      preparationStyleId: toStringValue(step.data.style),
      customPreparationStyle: toStringValue(step.data.style),
      preparationStyle: toStringValue(step.data.style),
      flameLevelId: toStringValue(step.data.flame),
      customFlameLevel: toStringValue(step.data.flame),
      flameLevel: toStringValue(step.data.flame),
      temperature: toStringValue(step.data.temperature),
      duration: toStringValue(step.data.duration),
      notes: toStringValue(step.data.notes),
    },
  })

  return {
    id: step.id,
    type: FLOW_NODE_TYPES.recipeStep,
    position: { x, y },
    draggable: true,
    selectable: true,
    deletable: true,
    connectable: true,
    style: {
      width: NODE_SIZES.recipeStep.width,
      height: NODE_SIZES.recipeStep.height,
    },
    data: {
      ...normalized,
      stepNumber,
    },
  }
}

const buildConditionNode = (step: RecipeExecutionStep, x: number, y: number): FlowNodePayload => {
  const normalized = normalizeConditionNodeData({
    title: toStringValue(step.data.title || step.data.question),
    condition: {
      question: toStringValue(step.data.title || step.data.question),
      expectedResult: toStringValue(step.data.expectedResult),
      successLabel: toStringValue(step.data.successLabel || step.data.yesLabel || 'Yes'),
      failureLabel: toStringValue(step.data.failureLabel || step.data.noLabel || 'No'),
      notes: toStringValue(step.data.notes),
    },
    yesLabel: toStringValue(step.data.successLabel || step.data.yesLabel || 'Yes'),
    noLabel: toStringValue(step.data.failureLabel || step.data.noLabel || 'No'),
    description: toStringValue(step.data.notes),
  })

  return {
    id: step.id,
    type: FLOW_NODE_TYPES.condition,
    position: { x, y },
    draggable: true,
    selectable: true,
    deletable: true,
    connectable: true,
    style: {
      width: NODE_SIZES.condition.width,
      height: NODE_SIZES.condition.height,
    },
    data: normalized,
  }
}

const buildParallelStartNode = (step: RecipeExecutionStep, x: number, y: number): FlowNodePayload => {
  const normalized = normalizeParallelNodeData({
    title: toStringValue(step.data.title),
    parallel: {
      kind: 'start',
      label: toStringValue(step.data.title),
      notes: toStringValue(step.data.notes),
    },
    description: toStringValue(step.data.notes),
  }, 'start')

  return {
    id: step.id,
    type: FLOW_NODE_TYPES.parallelStart,
    position: { x, y },
    draggable: true,
    selectable: true,
    deletable: true,
    connectable: true,
    style: {
      width: NODE_SIZES.parallelStart.width,
      height: NODE_SIZES.parallelStart.height,
    },
    data: normalized,
  }
}

const buildParallelEndNode = (step: RecipeExecutionStep, x: number, y: number): FlowNodePayload => {
  const normalized = normalizeParallelNodeData({
    title: toStringValue(step.data.title),
    parallel: {
      kind: 'end',
      label: toStringValue(step.data.title),
      notes: toStringValue(step.data.notes),
    },
    description: toStringValue(step.data.notes),
  }, 'end')

  return {
    id: step.id,
    type: FLOW_NODE_TYPES.parallelEnd,
    position: { x, y },
    draggable: true,
    selectable: true,
    deletable: true,
    connectable: true,
    style: {
      width: NODE_SIZES.parallelEnd.width,
      height: NODE_SIZES.parallelEnd.height,
    },
    data: normalized,
  }
}

const buildNodePayload = (step: RecipeExecutionStep, stepNumber: number, x: number, y: number): FlowNodePayload => {
  if (step.nodeType === 'condition') {
    return buildConditionNode(step, x, y)
  }

  if (step.nodeType === 'parallelStart') {
    return buildParallelStartNode(step, x, y)
  }

  if (step.nodeType === 'parallelEnd') {
    return buildParallelEndNode(step, x, y)
  }

  return buildRecipeStepNode(step, stepNumber, x, y)
}

const toConditionSourceHandle = (label?: string) => {
  const upper = toStringValue(label).trim().toUpperCase()
  if (upper === 'YES') return 'condition-yes'
  if (upper === 'NO') return 'condition-no'
  return null
}

export const convertRecipeExecutionModelToFlowData = (executionModel: RecipeExecutionModel): FlowData => {
  const steps = (executionModel.steps ?? []).filter((step) => step && step.id.trim())

  const edges: FlowEdgePayload[] = (executionModel.edges ?? [])
    .filter((edge) => edge && edge.from.trim() && edge.to.trim())
    .map((edge, index) => ({
      id: `generated-edge-${index + 1}-${edge.from}-${edge.to}`,
      source: edge.from,
      target: edge.to,
      sourceHandle: toConditionSourceHandle(edge.label),
      label: toStringValue(edge.label) || null,
    }))

  const orderedIds = buildTopologicalOrder(steps, edges)
  const stepById = new Map(steps.map((step) => [step.id, step]))
  const columns = Math.max(1, Math.ceil(Math.sqrt(Math.max(1, steps.length))))

  const nodes: FlowNodePayload[] = orderedIds
    .map((stepId) => stepById.get(stepId))
    .filter((step): step is RecipeExecutionStep => !!step)
    .map((step, index) => {
      const col = index % columns
      const row = Math.floor(index / columns)
      const x = ORIGIN_X + col * HORIZONTAL_GAP
      const y = ORIGIN_Y + row * VERTICAL_GAP
      return buildNodePayload(step, index + 1, x, y)
    })

  return {
    nodes,
    edges,
  }
}

export const normalizeRecipeExecutionModel = (value: unknown): RecipeExecutionModel => {
  if (!isRecord(value) || !Array.isArray(value.steps) || !Array.isArray(value.edges)) {
    throw new Error('Generated execution response is invalid.')
  }

  const steps = value.steps
    .map((entry, index) => toExecutionStep(entry, index))
    .filter((step): step is RecipeExecutionStep => !!step)

  const stepIdSet = new Set(steps.map((step) => step.id))

  const edges = value.edges
    .map((entry) => {
      if (!isRecord(entry)) return null
      const from = toStringValue(entry.from).trim()
      const to = toStringValue(entry.to).trim()
      if (!from || !to) return null
      if (!stepIdSet.has(from) || !stepIdSet.has(to)) return null
      const label = toStringValue(entry.label).trim()
      return { from, to, label: label || undefined }
    })
    .filter((edge): edge is NonNullable<typeof edge> => edge !== null)

  return { steps, edges }
}
