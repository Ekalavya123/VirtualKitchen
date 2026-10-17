import { useCallback, useMemo, useState } from 'react'
import type { Process } from '../../../../../types/process'
import { useIngredientCatalog } from '../../../../../shared/catalog/ingredientCatalog'
import { describePreparedReference } from '../../../model/preparationFlows'
import {
  applyRecipeStepFieldUpdate,
  findAnnotatedIngredientIndex,
  getStepAnnotationTargets,
  normalizeRecipeStepNodeData,
  stepAnnotationLabelResolver,
  withAnnotationTarget,
  withoutTargetsOfRemovedAnnotations,
  type ActionOnIngredient,
  type RecipeStepFields,
  type RecipeStepNodeData,
} from '../../../model/recipeStepData'
import { getStepOutputLabel, getStepOutputReferenceProblem, type StepOutputGraph } from '../../../model/recipeStepOutputs'
import { parsePreparedAnnotationId, sameAnnotationTarget, type Annotation, type StepAnnotationTargets } from '../../../model/stepAnnotations'
import AnnotatedTextEditor, { type ChipState } from './AnnotatedTextEditor'
import IngredientAmountPopover from './IngredientAmountPopover'
import { buildAnnotationOptions } from './annotationOptions'

export type StepDataUpdater = (nodeId: string, coalesceField: string, update: (data: unknown) => RecipeStepNodeData) => void

type StepDescriptionFieldProps = {
  nodeId: string
  step: RecipeStepFields
  recipeProcesses: Process[]
  isPreparation: boolean
  stepOutputGraph: StepOutputGraph
  /** One undoable change of the step's data (description and the target an annotation adds, together). */
  updateStepData: StepDataUpdater
  updateActionOnIngredients: (nodeId: string, ingredients: ActionOnIngredient[]) => void
  /** Opens the Preparation on the step that makes a prepared component. */
  onOpenSubprocess?: (processId: number, stepId?: string) => void
  autoFocus?: boolean
}

/**
 * A step's description with @ annotations, wired to the step: picking an entity adds it to the step's Action On
 * in the same undoable change; clicking an ingredient chip edits its amount; clicking a prepared chip opens the
 * flow that makes it. Chips show live names, and ones whose target the step no longer uses show as missing.
 */
export default function StepDescriptionField({
  nodeId,
  step,
  recipeProcesses,
  isPreparation,
  stepOutputGraph,
  updateStepData,
  updateActionOnIngredients,
  onOpenSubprocess,
  autoFocus,
}: StepDescriptionFieldProps) {
  const catalogVersion = useIngredientCatalog()
  const [amountEdit, setAmountEdit] = useState<{ index: number; anchor: DOMRect } | null>(null)

  const resolveLabel = useMemo(() => stepAnnotationLabelResolver({
    preparedName: (reference) => describePreparedReference(recipeProcesses, reference).label,
    stepOutputLabel: (stepId) => getStepOutputLabel(stepOutputGraph, stepId),
  }), [recipeProcesses, stepOutputGraph])

  const targets = getStepAnnotationTargets(step)
  const targetsKey = JSON.stringify(targets)
  const chipState = useCallback((annotation: Annotation): ChipState => {
    const ownTargets = JSON.parse(targetsKey) as StepAnnotationTargets
    if (!ownTargets[annotation.kind].some((id) => sameAnnotationTarget(annotation, annotation.kind, id))) return 'missing'
    if (annotation.kind === 'prepared') {
      const reference = parsePreparedAnnotationId(annotation.id)
      return reference && !describePreparedReference(recipeProcesses, reference).problem ? 'ok' : 'missing'
    }
    if (annotation.kind === 'step') return getStepOutputReferenceProblem(stepOutputGraph, annotation.id, nodeId) ? 'missing' : 'ok'
    return 'ok'
  }, [targetsKey, recipeProcesses, stepOutputGraph, nodeId])

  const getOptions = useCallback(
    (query: string) => buildAnnotationOptions({ nodeId, step, recipeProcesses, isPreparation, stepOutputGraph }, query),
    // catalogVersion: the ingredient catalog may finish loading while the editor is open.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [nodeId, step, recipeProcesses, isPreparation, stepOutputGraph, catalogVersion],
  )

  const handleChange = useCallback((value: string, inserted?: Annotation) => {
    updateStepData(nodeId, 'actionDescription', (data) => {
      const before = normalizeRecipeStepNodeData(data).step
      const next = applyRecipeStepFieldUpdate(data, 'actionDescription', value)
      // The step's targets follow its annotations: a picked one is added, a deleted one leaves "Used in this step".
      return withoutTargetsOfRemovedAnnotations(before, inserted ? withAnnotationTarget(next, inserted) : next)
    })
  }, [nodeId, updateStepData])

  const handleChipActivate = useCallback((annotation: Annotation, anchor: DOMRect) => {
    if (annotation.kind === 'ingredient') {
      // Right after an insert the step may not have re-rendered yet; the entry is the last one then.
      const index = findAnnotatedIngredientIndex(step, annotation.id)
      setAmountEdit({ index: index >= 0 ? index : step.actionOn.ingredients.length, anchor })
      return
    }
    if (annotation.kind === 'prepared') {
      const reference = parsePreparedAnnotationId(annotation.id)
      if (reference && !describePreparedReference(recipeProcesses, reference).problem) onOpenSubprocess?.(reference.processId, reference.stepId)
    }
  }, [step, recipeProcesses, onOpenSubprocess])

  const changeIngredient = (index: number, patch: Partial<ActionOnIngredient>) => {
    const ingredients = step.actionOn.ingredients.map((entry, at) => (at === index ? { ...entry, ...patch } : entry))
    updateActionOnIngredients(nodeId, ingredients)
  }

  return (
    <>
      <AnnotatedTextEditor
        value={step.actionDescription}
        onChange={handleChange}
        getOptions={getOptions}
        resolveLabel={resolveLabel}
        chipState={chipState}
        onChipActivate={handleChipActivate}
        placeholder="Describe what this step does — type @ to name an ingredient or prepared component"
        ariaLabel="Action description"
        autoFocus={autoFocus}
      />
      {amountEdit && step.actionOn.ingredients[amountEdit.index] && (
        <IngredientAmountPopover
          step={step}
          index={amountEdit.index}
          anchor={amountEdit.anchor}
          onChange={changeIngredient}
          onClose={() => setAmountEdit(null)}
        />
      )}
    </>
  )
}
