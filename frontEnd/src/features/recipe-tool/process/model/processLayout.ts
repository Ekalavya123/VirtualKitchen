/**
 * Row layout for a process canvas: steps read like text — left to right in rows of 1–3 (as many as
 * fit the canvas width), wrapping to the start of the next row. Every row fills the width and cards
 * in a row share one height. A check (CONDITION) gets a row of its own, right below the node before
 * it, and the steps after it continue on a new row.
 *
 * Handle sides follow the same reading direction and are derived from positions (never stored): a
 * node continues to the right while its next node is beside it in the same row, and drops to the next
 * row's first node from its bottom. Old vertically laid out processes keep top/bottom handles.
 *
 * Pure, with no runtime imports, so it is shared by AI generation (recipeProcessGenerationConverter.ts)
 * and the canvas's Auto Arrange, and runs under `node --test`.
 */

export type LayoutEdge = { id: string; source: string; target: string; sourceHandle?: string | null }
export type LayoutRect = { x: number; y: number; width: number; height: number }
export type LayoutBox = LayoutRect & { id: string }
export type RowPlan = { perRow: number; cellWidth: number }

export type HandleSides = {
  target: 'top' | 'left'
  /** A STEP's source, or a CONDITION's Yes. */
  source: 'right' | 'bottom'
  /** A CONDITION's No: whichever side the other two leave free. */
  no: 'left' | 'bottom' | 'right'
}

/** The sides a node uses when it has no entry: top in, bottom out; a check's Yes right, No left. */
export const DEFAULT_HANDLE_SIDES: HandleSides = { target: 'top', source: 'bottom', no: 'left' }

export const ROW_LAYOUT = {
  /** Between cards in a row — room for an arrowhead and a Yes/No label. */
  columnGap: 96,
  /** Between rows — room for the edge from a row's last card down to the next row's first. */
  rowGap: 96,
  /** Kept free around the arranged graph inside the canvas. */
  padding: 48,
  /** Narrowest cell a card is comfortable to read in; decides how many fit per row. */
  minCellWidth: 300,
  maxPerRow: 3,
  conditionMinSize: 190,
  conditionMaxSize: 260,
  // Mirror RecipeStepNode's resize minimums.
  stepMinWidth: 240,
  stepMinHeight: 140,
}

const NO_HANDLE = 'condition-no'

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

/**
 * Edges that loop back to an earlier node, found with a depth-first walk from the flow's starting
 * points (nodes nothing leads into) in array order, a check's No edge taken last — so the edge that
 * closes a "repeat until" cycle is its No edge, never the Yes edge carrying the flow forward.
 */
export const findLoopEdges = (nodes: ReadonlyArray<{ id: string }>, edges: ReadonlyArray<LayoutEdge>): Set<string> => {
  const index = new Map(nodes.map((node, i) => [node.id, i]))
  const outgoing = new Map<string, LayoutEdge[]>()
  const hasIncoming = new Set<string>()
  for (const edge of edges) {
    if (!index.has(edge.source) || !index.has(edge.target)) continue
    outgoing.set(edge.source, [...(outgoing.get(edge.source) ?? []), edge])
    if (edge.source !== edge.target) hasIncoming.add(edge.target)
  }
  const forwardFirst = (edge: LayoutEdge) => (edge.sourceHandle === NO_HANDLE ? 1 : 0)
  for (const list of outgoing.values()) {
    list.sort((a, b) => forwardFirst(a) - forwardFirst(b) || (index.get(a.target) ?? 0) - (index.get(b.target) ?? 0))
  }

  const loops = new Set<string>()
  const state = new Map<string, 'active' | 'done'>()
  const visit = (id: string) => {
    state.set(id, 'active')
    for (const edge of outgoing.get(id) ?? []) {
      const targetState = state.get(edge.target)
      if (targetState === 'active') loops.add(edge.id)
      else if (targetState == null) visit(edge.target)
    }
    state.set(id, 'done')
  }
  const roots = nodes.filter((node) => !hasIncoming.has(node.id))
  for (const node of [...roots, ...nodes]) if (!state.has(node.id)) visit(node.id)
  return loops
}

/** How many cards fit per row in `availableWidth` (1–3), and how wide each cell is so the row fills it. */
export const planRows = (availableWidth: number): RowPlan => {
  const { columnGap, minCellWidth, maxPerRow, stepMinWidth } = ROW_LAYOUT
  const perRow = clamp(Math.floor((availableWidth + columnGap) / (minCellWidth + columnGap)), 1, maxPerRow)
  const cellWidth = Math.max(stepMinWidth, Math.floor((availableWidth - (perRow - 1) * columnGap) / perRow))
  return { perRow, cellWidth }
}

/**
 * Positions and sizes for `ordered` (reading order), the first row's top-left cell at `origin`.
 * STEPs wrap into rows of `plan.perRow`; `height` is a step's natural content height at the cell
 * width — a row is as tall as its tallest step, and every step in it takes that height. A CONDITION
 * ends the row before it and sits alone on its own row, a square centered right below the node
 * before it; the steps after it start a new row.
 */
export const arrangeRows = (
  ordered: ReadonlyArray<{ id: string; kind?: string; height: number }>,
  plan: RowPlan,
  origin: { x: number; y: number } = { x: 0, y: 0 },
): Map<string, LayoutRect> => {
  const { columnGap, rowGap, conditionMinSize, conditionMaxSize, stepMinHeight } = ROW_LAYOUT
  const result = new Map<string, LayoutRect>()
  const cellX = (column: number) => origin.x + column * (plan.cellWidth + columnGap)
  const conditionSize = clamp(plan.cellWidth, conditionMinSize, conditionMaxSize)
  let top = origin.y
  /** Horizontal center of the node placed last — where a following check goes. */
  let previousCenter = cellX(0) + plan.cellWidth / 2
  let row: { id: string; height: number }[] = []

  const placeStepRow = () => {
    if (row.length === 0) return
    const height = Math.round(Math.max(...row.map((node) => Math.max(stepMinHeight, node.height))))
    row.forEach((node, column) => {
      result.set(node.id, { x: Math.round(cellX(column)), y: Math.round(top), width: plan.cellWidth, height })
    })
    previousCenter = cellX(row.length - 1) + plan.cellWidth / 2
    top += height + rowGap
    row = []
  }

  for (const node of ordered) {
    if (node.kind !== 'CONDITION') {
      row.push(node)
      if (row.length === plan.perRow) placeStepRow()
      continue
    }
    placeStepRow()
    result.set(node.id, { x: Math.round(previousCenter - conditionSize / 2), y: Math.round(top), width: conditionSize, height: conditionSize })
    top += conditionSize + rowGap
  }
  placeStepRow()
  return result
}

/** `b` sits in the same row as `a` (their boxes overlap vertically), entirely to its right. */
const isRightOfInRow = (a: LayoutRect, b: LayoutRect) =>
  a.y < b.y + b.height && b.y < a.y + a.height && b.x >= a.x + a.width

/**
 * Which side each node's handles sit on, from where its neighbours are: target on the left when the
 * node it continues from is beside it to the left (else on top); source — a check's Yes — on the
 * right when the node it leads to is beside it to the right (else at the bottom); a check's No on
 * the first free side of left, bottom, right. Loop edges don't count — they route around.
 */
export const getHandleSides = (
  boxes: ReadonlyArray<LayoutBox>,
  edges: ReadonlyArray<LayoutEdge>,
  loopEdgeIds: ReadonlySet<string>,
): Map<string, HandleSides> => {
  const byId = new Map(boxes.map((box) => [box.id, box]))
  const targetLeft = new Set<string>()
  const sourceRight = new Set<string>()
  for (const edge of edges) {
    if (loopEdgeIds.has(edge.id) || edge.source === edge.target) continue
    const source = byId.get(edge.source)
    const target = byId.get(edge.target)
    if (!source || !target || !isRightOfInRow(source, target)) continue
    targetLeft.add(target.id)
    if (edge.sourceHandle !== NO_HANDLE) sourceRight.add(source.id)
  }

  const sides = new Map<string, HandleSides>()
  for (const box of boxes) {
    const target = targetLeft.has(box.id) ? 'left' : 'top'
    const source = sourceRight.has(box.id) ? 'right' : 'bottom'
    const no = target !== 'left' ? 'left' : source !== 'bottom' ? 'bottom' : 'right'
    sides.set(box.id, { target, source, no })
  }
  return sides
}
