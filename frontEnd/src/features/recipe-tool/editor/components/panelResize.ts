import type { MouseEvent as ReactMouseEvent } from 'react'

/**
 * Starts dragging a side panel's resize handle: follows the mouse until it's released, reporting the new
 * width clamped to [minWidth, maxWidth()]. `side` is where the panel sits — dragging towards the canvas grows
 * it. `maxWidth` is read on every move, so it can leave room for the other panel and the canvas.
 */
export function startPanelResize(event: ReactMouseEvent, { side, startWidth, minWidth, maxWidth, onResize }: {
  side: 'left' | 'right'
  startWidth: number
  minWidth: number
  maxWidth: () => number
  onResize: (width: number) => void
}) {
  const startX = event.clientX
  const onMove = (moveEvent: MouseEvent) => {
    const delta = side === 'left' ? moveEvent.clientX - startX : startX - moveEvent.clientX
    const effectiveMax = maxWidth()
    let nextWidth = startWidth + delta
    if (nextWidth < minWidth) nextWidth = minWidth
    if (nextWidth > effectiveMax) nextWidth = Math.max(minWidth, effectiveMax)
    onResize(nextWidth)
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  event.preventDefault()
}
