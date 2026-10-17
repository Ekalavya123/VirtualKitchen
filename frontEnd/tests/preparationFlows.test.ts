import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  describePreparedReference,
  getPreparationFlows,
  getPreparedOutputs,
} from '../src/features/recipe-tool/model/preparationFlows.ts'
import { mergePreparations } from '../src/features/recipe-tool/model/mergePreparations.ts'
import type { Process, ProcessEdge, ProcessNode } from '../src/types/process.ts'

const step = (id: string, expectedOutput = '', processes: Array<{ processId: number; stepId?: string }> = [], y = 0): ProcessNode => ({
  id, kind: 'STEP', position: { x: 0, y }, width: 280, height: 160,
  data: { step: { action: 'chop', actionDescription: id, expectedOutput, actionOn: { ingredients: [], processes, steps: [] } } },
})
const check = (id: string): ProcessNode => ({ id, kind: 'CONDITION', position: { x: 0, y: 0 }, data: { title: id } })
const edge = (source: string, target: string, sourceHandle?: string): ProcessEdge =>
  (sourceHandle ? { id: `${source}-${target}`, source, target, sourceHandle } : { id: `${source}-${target}`, source, target })
const process = (id: number, type: Process['type'], name: string, nodes: ProcessNode[], edges: ProcessEdge[] = []): Process =>
  ({ id, type, recipeId: 7, name, nodes, edges })

describe('getPreparationFlows', () => {
  it('splits the Preparation into flows, each named by its final step', () => {
    const prep = process(2, 'SUBPROCESS', 'Preparation', [
      step('a1'), step('a2', 'Marinated Chicken'),
      step('b1', 'Chopped Onions'),
    ], [edge('a1', 'a2')])

    const flows = getPreparationFlows(prep)

    assert.deepEqual(flows.map((flow) => flow.outputName), ['Marinated Chicken', 'Chopped Onions'])
    assert.deepEqual(flows.map((flow) => flow.output?.id), ['a2', 'b1'])
  })

  it('ignores a check and its loop back when finding the final step', () => {
    // fry -> "golden?" (yes -> rest, no -> fry): the flow ends at rest.
    const prep = process(2, 'SUBPROCESS', 'Preparation', [step('fry'), check('golden'), step('rest', 'Fried Onions')], [
      edge('fry', 'golden'), edge('golden', 'rest', 'condition-yes'), edge('golden', 'fry', 'condition-no'),
    ])

    const [flow] = getPreparationFlows(prep)
    assert.deepEqual(flow.endSteps.map((node) => node.id), ['rest'])
    assert.equal(flow.outputName, 'Fried Onions')
  })

  it('reports every end of a flow that branches', () => {
    const prep = process(2, 'SUBPROCESS', 'Preparation', [step('a'), step('b', 'B'), step('c', 'C')], [edge('a', 'b'), edge('a', 'c')])
    assert.equal(getPreparationFlows(prep)[0].endSteps.length, 2)
  })
})

describe('getPreparedOutputs / describePreparedReference', () => {
  const prep = process(2, 'SUBPROCESS', 'Preparation', [step('a1', 'Cut Chicken'), step('a2', 'Marinated Chicken'), step('b1')], [edge('a1', 'a2')])
  const main = process(1, 'MAIN', 'Curry', [step('m1')])

  it('offers each flow output first, then intermediate results; steps without an output are not offered', () => {
    const outputs = getPreparedOutputs([main, prep])
    assert.deepEqual(outputs.map((output) => [output.label, output.isFlowOutput]), [['Marinated Chicken', true], ['Cut Chicken', false]])
  })

  it('resolves a use to the live name, and explains one that broke', () => {
    assert.deepEqual(describePreparedReference([main, prep], { processId: 2, stepId: 'a2' }), { label: 'Marinated Chicken', problem: null })
    assert.equal(describePreparedReference([main, prep], { processId: 2, stepId: 'gone' }).problem, 'missing-step')
    assert.equal(describePreparedReference([main, prep], { processId: 2, stepId: 'b1' }).problem, 'no-output')
    assert.equal(describePreparedReference([main, prep], { processId: 9 }).problem, 'missing-process')
    assert.deepEqual(describePreparedReference([main, prep], { processId: 2 }), { label: 'Preparation', problem: null })
  })
})

describe('mergePreparations', () => {
  it('leaves a recipe with one preparation alone', () => {
    assert.equal(mergePreparations([process(1, 'MAIN', 'Main', []), process(2, 'SUBPROCESS', 'Preparation', [])]), null)
  })

  it('folds subprocesses into one Preparation and points whole-subprocess uses at their output step', () => {
    const onions = process(5, 'SUBPROCESS', 'Chopped Onions', [step('o1')])
    const chicken = process(3, 'SUBPROCESS', 'Marinated Chicken', [step('c1'), step('c2', 'marinated chicken', [{ processId: 5 }], 200)], [edge('c1', 'c2')])
    const main = process(1, 'MAIN', 'Curry', [step('m1', '', [{ processId: 5 }, { processId: 3 }])])

    const merged = mergePreparations([main, onions, chicken])
    assert.ok(merged)

    assert.equal(merged.preparation.id, 3, 'the oldest saved subprocess is kept')
    assert.equal(merged.preparation.name, 'Preparation')
    assert.deepEqual(merged.removedIds, [5])
    assert.deepEqual(merged.preparation.nodes.map((node) => node.id).sort(), ['c1', 'c2', 'o1'])
    assert.equal(merged.preparation.edges.length, 1)

    const mainUses = (merged.changed[0].nodes[0].data as { step: { actionOn: { processes: unknown[] } } }).step.actionOn.processes
    assert.deepEqual(mainUses, [{ processId: 3, stepId: 'o1' }, { processId: 3, stepId: 'c2' }])

    const byId = new Map(merged.preparation.nodes.map((node) => [node.id, node]))
    const onionStep = (byId.get('o1')!.data as { step: { expectedOutput: string } }).step
    assert.equal(onionStep.expectedOutput, 'Chopped Onions', 'an unnamed output takes the old subprocess name')
    const chickenUses = (byId.get('c2')!.data as { step: { actionOn: { processes: unknown[] } } }).step.actionOn.processes
    assert.deepEqual(chickenUses, [], 'a preparation never uses another preparation')
    assert.equal(merged.droppedReferences, 1)

    // The two old subprocesses no longer overlap.
    const xs = merged.preparation.nodes.map((node) => node.position?.x ?? 0)
    assert.notEqual(Math.min(...xs), Math.max(...xs))
  })

  it('moves prepared annotations in descriptions along with the uses they mirror', () => {
    const onions = process(5, 'SUBPROCESS', 'Chopped Onions', [step('o1')])
    const chicken = process(3, 'SUBPROCESS', 'Marinated Chicken', [step('c1', 'Marinated Chicken')])
    const fry = step('m1', '', [{ processId: 5 }, { processId: 3, stepId: 'c1' }])
    ;(fry.data as { step: { actionDescription: string } }).step.actionDescription =
      'Fry @[onions](prepared:5) with @[chicken](prepared:3:c1) and @[salt](ingredient:33)'
    const inside = step('c2', 'x', [{ processId: 5 }])
    ;(inside.data as { step: { actionDescription: string } }).step.actionDescription = 'Mix in @[onions](prepared:5)'
    chicken.nodes.push(inside)

    const merged = mergePreparations([process(1, 'MAIN', 'Curry', [fry]), onions, chicken])
    assert.ok(merged)

    const description = (node: ProcessNode) => (node.data as { step: { actionDescription: string } }).step.actionDescription
    assert.equal(description(merged.changed[0].nodes[0]), 'Fry @[onions](prepared:3:o1) with @[chicken](prepared:3:c1) and @[salt](ingredient:33)')
    assert.equal(description(merged.preparation.nodes.find((node) => node.id === 'c2')!), 'Mix in onions')
  })
})
