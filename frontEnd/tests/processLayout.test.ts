import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  ROW_LAYOUT,
  arrangeRows,
  findLoopEdges,
  getHandleSides,
  planRows,
  type LayoutBox,
  type LayoutEdge,
} from '../src/features/recipe-tool/process/model/processLayout.ts'

const edge = (source: string, target: string, sourceHandle?: string): LayoutEdge =>
  ({ id: `${source}->${target}${sourceHandle ? `:${sourceHandle}` : ''}`, source, target, sourceHandle })
const yes = (source: string, target: string) => edge(source, target, 'condition-yes')
const no = (source: string, target: string) => edge(source, target, 'condition-no')
const chain = (ids: string[]) => ids.slice(1).map((id, i) => edge(ids[i], id))
const step = (id: string, height = 160) => ({ id, kind: 'STEP', height })
const check = (id: string) => ({ id, kind: 'CONDITION', height: 190 })

const boxesOf = (layout: Map<string, { x: number; y: number; width: number; height: number }>): LayoutBox[] =>
  [...layout].map(([id, rect]) => ({ id, ...rect }))

describe('planRows', () => {
  it('fits 1 to 3 cards per row depending on the width, never more', () => {
    assert.equal(planRows(360).perRow, 1)
    assert.equal(planRows(800).perRow, 2)
    assert.equal(planRows(1244).perRow, 3)
    assert.equal(planRows(1600).perRow, 3)
    assert.equal(planRows(4000).perRow, 3)
  })

  it('makes a row fill the width exactly', () => {
    const { perRow, cellWidth } = planRows(1244)
    assert.ok(perRow * cellWidth + (perRow - 1) * ROW_LAYOUT.columnGap <= 1244)
    assert.ok(perRow * (cellWidth + 1) + (perRow - 1) * ROW_LAYOUT.columnGap > 1244)
  })

  it('never makes a cell narrower than a card can be', () => {
    assert.equal(planRows(100).cellWidth, ROW_LAYOUT.stepMinWidth)
  })
})

describe('arrangeRows', () => {
  it('wraps into left-aligned rows, every row starting at the left', () => {
    const plan = { perRow: 3, cellWidth: 350 }
    const layout = arrangeRows(['a', 'b', 'c', 'd', 'e'].map((id) => step(id)), plan, { x: 10, y: 20 })
    const at = (id: string) => layout.get(id) as { x: number; y: number }
    assert.deepEqual([at('a').x, at('b').x, at('c').x], [10, 10 + 350 + ROW_LAYOUT.columnGap, 10 + 2 * (350 + ROW_LAYOUT.columnGap)])
    assert.equal(at('d').x, 10)
    assert.equal(at('e').x, at('b').x)
    assert.equal(at('a').y, 20)
    assert.equal(at('d').y, 20 + 160 + ROW_LAYOUT.rowGap)
  })

  it('gives every step in a row the cell width and the row\'s tallest height', () => {
    const layout = arrangeRows([step('a', 150), step('b', 260), step('c', 200), step('d', 170)], { perRow: 3, cellWidth: 320 })
    for (const id of ['a', 'b', 'c']) assert.deepEqual([layout.get(id)?.width, layout.get(id)?.height], [320, 260])
    assert.equal(layout.get('d')?.height, 170)
    assert.equal(layout.get('d')?.y, 260 + ROW_LAYOUT.rowGap)
  })

  it('puts a check alone on its own row, right below the node before it, and continues on a new row', () => {
    // a b | c? | d e f | g
    const layout = arrangeRows([step('a', 200), step('b', 240), check('c'), step('d'), step('e'), step('f'), step('g')], { perRow: 3, cellWidth: 340 })
    const box = (id: string) => layout.get(id) as { x: number; y: number; width: number; height: number }
    assert.equal(box('c').width, box('c').height)
    assert.equal(box('c').width, ROW_LAYOUT.conditionMaxSize)
    assert.equal(box('c').x + box('c').width / 2, box('b').x + 340 / 2, 'centered under b')
    assert.equal(box('c').y, 240 + ROW_LAYOUT.rowGap)
    assert.equal(box('d').x, 0)
    assert.equal(box('d').y, box('c').y + box('c').height + ROW_LAYOUT.rowGap)
    assert.equal(box('f').y, box('d').y)
    assert.equal(box('g').x, 0)
    assert.ok(box('g').y > box('d').y)
  })

  it('stacks consecutive checks below each other, and starts with one at the left', () => {
    const layout = arrangeRows([check('c1'), check('c2'), step('a')], { perRow: 3, cellWidth: 340 })
    assert.equal(layout.get('c1')?.x, layout.get('c2')?.x)
    assert.equal((layout.get('c1')?.x ?? 0) + ROW_LAYOUT.conditionMaxSize / 2, 340 / 2)
    assert.ok((layout.get('c2')?.y ?? 0) > (layout.get('c1')?.y ?? 0))
  })

  it('keeps a large recipe free of overlaps', () => {
    const nodes = Array.from({ length: 50 }, (_, i) => (i % 6 === 5 ? check(`n${i}`) : step(`n${i}`, 140 + (i % 5) * 37)))
    const boxes = boxesOf(arrangeRows(nodes, planRows(1244)))
    for (let a = 0; a < boxes.length; a += 1) {
      for (let b = a + 1; b < boxes.length; b += 1) {
        const p = boxes[a]
        const q = boxes[b]
        const apart = p.x + p.width + 40 <= q.x || q.x + q.width + 40 <= p.x || p.y + p.height + 40 <= q.y || q.y + q.height + 40 <= p.y
        assert.ok(apart, `${p.id} and ${q.id} are too close`)
      }
    }
  })
})

describe('getHandleSides', () => {
  it('reads a wrapped chain row by row: top/right, left/right, left/bottom, then top again', () => {
    const ids = ['a', 'b', 'c', 'd', 'e']
    const boxes = boxesOf(arrangeRows(ids.map((id) => step(id)), { perRow: 3, cellWidth: 320 }))
    const sides = getHandleSides(boxes, chain(ids), new Set())
    assert.deepEqual([sides.get('a')?.target, sides.get('a')?.source], ['top', 'right'])
    assert.deepEqual([sides.get('b')?.target, sides.get('b')?.source], ['left', 'right'])
    assert.deepEqual([sides.get('c')?.target, sides.get('c')?.source], ['left', 'bottom'])
    assert.deepEqual([sides.get('d')?.target, sides.get('d')?.source], ['top', 'right'])
    assert.deepEqual([sides.get('e')?.target, sides.get('e')?.source], ['left', 'bottom'])
  })

  it('keeps top/bottom handles for a vertically laid out (older) process', () => {
    const boxes: LayoutBox[] = [
      { id: 'a', x: 0, y: 0, width: 280, height: 160 },
      { id: 'b', x: 0, y: 260, width: 280, height: 160 },
    ]
    const sides = getHandleSides(boxes, [edge('a', 'b')], new Set())
    assert.deepEqual(sides.get('a'), { target: 'top', source: 'bottom', no: 'left' })
    assert.deepEqual(sides.get('b'), { target: 'top', source: 'bottom', no: 'left' })
  })

  it('connects an arranged check top in, Yes down to the next row, No out to the left', () => {
    // prep boil | done? | drain serve
    const nodes = [step('prep'), step('boil'), check('done'), step('drain'), step('serve')]
    const edges = [edge('prep', 'boil'), edge('boil', 'done'), yes('done', 'drain'), no('done', 'boil'), edge('drain', 'serve')]
    const boxes = boxesOf(arrangeRows(nodes, { perRow: 3, cellWidth: 320 }))
    const sides = getHandleSides(boxes, edges, findLoopEdges(nodes, edges))
    assert.equal(sides.get('boil')?.source, 'bottom', 'boil drops to the check below it')
    assert.deepEqual(sides.get('done'), { target: 'top', source: 'bottom', no: 'left' })
    assert.deepEqual([sides.get('drain')?.target, sides.get('drain')?.source], ['top', 'right'])
  })

  it('puts a check\'s No on whichever side is free when it is dragged into a row', () => {
    const boxes: LayoutBox[] = [
      { id: 'a', x: 0, y: 0, width: 300, height: 200 },
      { id: 'c', x: 400, y: 0, width: 200, height: 200 },
      { id: 'b', x: 700, y: 0, width: 300, height: 200 },
      { id: 'z', x: 400, y: 400, width: 300, height: 200 },
    ]
    const middle = getHandleSides(boxes, [edge('a', 'c'), yes('c', 'b')], new Set())
    assert.deepEqual(middle.get('c'), { target: 'left', source: 'right', no: 'bottom' })
    const last = getHandleSides(boxes, [edge('a', 'c'), yes('c', 'z')], new Set())
    assert.deepEqual(last.get('c'), { target: 'left', source: 'bottom', no: 'right' })
  })

  it('ignores loop edges when picking sides', () => {
    const boxes = boxesOf(arrangeRows([step('a'), step('b')], { perRow: 2, cellWidth: 320 }))
    const loop = edge('b', 'a')
    const sides = getHandleSides(boxes, [edge('a', 'b'), loop], new Set([loop.id]))
    assert.equal(sides.get('a')?.target, 'top')
    assert.equal(sides.get('b')?.source, 'bottom')
  })
})

describe('findLoopEdges', () => {
  it('finds a check\'s No edge back to the step it checks, not the forward Yes edge', () => {
    const nodes = [step('boil'), check('done'), step('drain')]
    const edges = [edge('boil', 'done'), yes('done', 'drain'), no('done', 'boil')]
    assert.deepEqual([...findLoopEdges(nodes, edges)], [no('done', 'boil').id])
  })

  it('finds nothing in an acyclic flow', () => {
    assert.equal(findLoopEdges([step('a'), step('b')], [edge('a', 'b')]).size, 0)
  })
})
