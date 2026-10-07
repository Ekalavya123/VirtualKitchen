import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  EDIT_START,
  detachNode,
  findFreePosition,
  insertNodeAfter,
  preferredInsertPosition,
  type ProcessGraph,
} from '../src/features/recipe-tool/process/adapters/recipeProcessEditGraph.ts'
import type { ProcessEdge, ProcessNode } from '../src/types/process.ts'

const step = (id: string, y: number): ProcessNode => ({ id, kind: 'STEP', position: { x: 120, y }, width: 280, height: 160, data: { title: id } })
const check = (id: string, y: number): ProcessNode => ({ id, kind: 'CONDITION', position: { x: 120, y }, width: 190, height: 190, data: { title: id } })
const edge = (id: string, source: string, target: string, sourceHandle?: string): ProcessEdge =>
  (sourceHandle ? { id, source, target, sourceHandle } : { id, source, target })

/** boil -> add -> cook -> drain */
const pasta = (): ProcessGraph => ({
  nodes: [step('boil', 0), step('add', 260), step('cook', 520), step('drain', 780)],
  edges: [edge('e1', 'boil', 'add'), edge('e2', 'add', 'cook'), edge('e3', 'cook', 'drain')],
})

const ids = () => {
  let next = 0
  return () => `new-edge-${++next}`
}

const links = (graph: ProcessGraph) =>
  graph.edges.map((e) => `${e.source}->${e.target}${e.sourceHandle ? `(${e.sourceHandle})` : ''}`).sort()

describe('insertNodeAfter', () => {
  it('splices a step between the anchor and its follower, keeping the redirected edge id', () => {
    const result = insertNodeAfter(pasta(), step('season', 650), 'cook', 'boil', ids())

    assert.deepEqual(links(result), ['add->cook', 'boil->add', 'cook->season', 'season->drain'])
    assert.equal(result.edges.find((e) => e.source === 'cook')?.id, 'e3')
    assert.equal(result.nodes.length, 5)
  })

  it('appends after the last node', () => {
    const result = insertNodeAfter(pasta(), step('garnish', 1040), 'drain', 'boil', ids())

    assert.deepEqual(links(result), ['add->cook', 'boil->add', 'cook->drain', 'drain->garnish'])
  })

  it('inserts before the first node for START', () => {
    const result = insertNodeAfter(pasta(), step('wash', -260), EDIT_START, 'boil', ids())

    assert.ok(links(result).includes('wash->boil'))
    assert.equal(result.edges.length, 4)
  })

  it('wires a new check with Yes forward and No back to the step it checks', () => {
    const result = insertNodeAfter(pasta(), check('done?', 650), 'cook', 'boil', ids())

    assert.deepEqual(links(result), ['add->cook', 'boil->add', 'cook->done?', 'done?->cook(condition-no)', 'done?->drain(condition-yes)'])
  })

  it('continues a condition along its Yes branch, not its No loop', () => {
    const graph: ProcessGraph = {
      nodes: [step('cook', 0), check('done?', 260), step('drain', 520)],
      edges: [edge('e1', 'cook', 'done?'), edge('yes', 'done?', 'drain', 'condition-yes'), edge('no', 'done?', 'cook', 'condition-no')],
    }

    const result = insertNodeAfter(graph, step('rest', 400), 'done?', 'cook', ids())

    assert.deepEqual(links(result), ['cook->done?', 'done?->cook(condition-no)', 'done?->rest(condition-yes)', 'rest->drain'])
  })
})

describe('detachNode', () => {
  it('reconnects the predecessor to the successor', () => {
    const result = detachNode(pasta(), 'add', ids())

    assert.deepEqual(links(result), ['boil->cook', 'cook->drain'])
    assert.ok(!result.nodes.some((n) => n.id === 'add'))
  })

  it('removing the last node leaves the rest untouched', () => {
    const result = detachNode(pasta(), 'drain', ids())

    assert.deepEqual(links(result), ['add->cook', 'boil->add'])
  })

  it('removing a check drops its No loop and keeps the flow going', () => {
    const graph: ProcessGraph = {
      nodes: [step('cook', 0), check('done?', 260), step('drain', 520)],
      edges: [edge('e1', 'cook', 'done?'), edge('yes', 'done?', 'drain', 'condition-yes'), edge('no', 'done?', 'cook', 'condition-no')],
    }

    assert.deepEqual(links(detachNode(graph, 'done?', ids())), ['cook->drain'])
  })

  it('keeps a Yes handle when the removed node was on a Yes branch', () => {
    const graph: ProcessGraph = {
      nodes: [step('cook', 0), check('done?', 260), step('rest', 520), step('drain', 780)],
      edges: [edge('e1', 'cook', 'done?'), edge('yes', 'done?', 'rest', 'condition-yes'), edge('no', 'done?', 'cook', 'condition-no'), edge('e2', 'rest', 'drain')],
    }

    assert.deepEqual(links(detachNode(graph, 'rest', ids())), ['cook->done?', 'done?->cook(condition-no)', 'done?->drain(condition-yes)'])
  })
})

describe('placement', () => {
  it('prefers the space between the anchor and its follower', () => {
    assert.deepEqual(preferredInsertPosition(pasta(), 'cook', 'boil'), { x: 160, y: 690 })
  })

  it('nudges a position down until it is clear of existing nodes', () => {
    const nodes = [step('a', 0)]
    const free = findFreePosition(nodes, { x: 120, y: 0 }, { width: 280, height: 160 })

    assert.ok(free.y >= 160)
    assert.equal(free.x, 120)
  })
})
