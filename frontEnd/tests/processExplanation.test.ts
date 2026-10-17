import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  explainPreparationFlows,
  nodesContinuingPrevious,
  preparationFlowTitle,
} from '../src/features/recipe-tool/view/model/processExplanation.ts'
import type { Process, ProcessEdge, ProcessNode } from '../src/types/process.ts'

const step = (id: string, expectedOutput = ''): ProcessNode => ({
  id, kind: 'STEP', position: { x: 0, y: 0 }, data: { step: { action: 'chop', actionDescription: id, expectedOutput } },
})
const edge = (source: string, target: string, sourceHandle?: string): ProcessEdge =>
  (sourceHandle ? { id: `${source}-${target}`, source, target, sourceHandle } : { id: `${source}-${target}`, source, target })
const prep = (nodes: ProcessNode[], edges: ProcessEdge[] = []): Process => ({ id: 2, type: 'SUBPROCESS', recipeId: 1, name: 'Preparation', nodes, edges })

describe('explainPreparationFlows', () => {
  it('gives a single-step flow one point, titled from its output', () => {
    const [flow] = explainPreparationFlows(prep([step('chop', 'Chopped Onions')]), ['chop'])
    assert.deepEqual(flow.nodeIds, ['chop'])
    assert.equal(flow.title, 'Prepare Chopped Onions')
    assert.equal(flow.outputNodeId, 'chop')
  })

  it('keeps a multi-step flow in cooking order, and orders flows by where they start', () => {
    // Stored out of order; reading order says: onions first, then cut -> marinate.
    const process = prep([step('marinate', 'Marinated Chicken'), step('onions', 'Chopped Onions'), step('cut', 'Cut Chicken')], [edge('cut', 'marinate')])
    const flows = explainPreparationFlows(process, ['onions', 'cut', 'marinate'])
    assert.deepEqual(flows.map((flow) => flow.title), ['Prepare Chopped Onions', 'Prepare Marinated Chicken'])
    assert.deepEqual(flows[1].nodeIds, ['cut', 'marinate'])
    assert.equal(flows[1].outputName, 'Marinated Chicken')
  })

  it('never invents a name for a flow without a named output', () => {
    const [flow] = explainPreparationFlows(prep([step('a')]), ['a'])
    assert.equal(flow.title, 'Preparation flow 1')
    assert.equal(flow.outputName, '')
    assert.equal(preparationFlowTitle('  ', 2), 'Preparation flow 3')
  })
})

describe('nodesContinuingPrevious', () => {
  it('marks only items an edge leads to from the item right before them', () => {
    // heat -> fry -> golden? (yes -> simmer, no -> fry); serve is unconnected.
    const process = { edges: [edge('heat', 'fry'), edge('fry', 'golden'), edge('golden', 'simmer', 'condition-yes'), edge('golden', 'fry', 'condition-no')] }
    const continuing = nodesContinuingPrevious(process, ['heat', 'fry', 'golden', 'simmer', 'serve'])
    assert.deepEqual([...continuing].sort(), ['fry', 'golden', 'simmer'])
  })

  it('does not count a loop back as carrying on', () => {
    const continuing = nodesContinuingPrevious({ edges: [edge('b', 'a', 'condition-no')] }, ['b', 'a'])
    assert.equal(continuing.size, 0)
  })
})
