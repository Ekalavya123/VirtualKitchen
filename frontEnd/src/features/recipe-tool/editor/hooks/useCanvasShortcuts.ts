import { useEffect } from 'react'

/**
 * Keyboard shortcuts (Delete/Backspace, Ctrl/Cmd+Z, Ctrl/Cmd+Y) — a global listener so shortcuts work
 * regardless of which element inside the canvas currently has focus, not only while the React Flow pane
 * itself is focused. Skips typing in an input/textarea/select or an editable text (the @ description editor), and is scoped to specific key combos only, so it
 * never interferes with browser/app navigation shortcuts.
 *
 * This is the *only* keyboard-delete path — React Flow's own built-in one is disabled (`deleteKeyCode={null}`
 * on the canvas) so a single press can't be handled twice, and so edges (whose selection React Flow tracks
 * independently) are covered too.
 */
export function useCanvasShortcuts({ onDelete, onUndo, onRedo }: {
  onDelete: () => void
  onUndo: () => void
  onRedo: () => void
}) {
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null
      const tag = target?.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target?.isContentEditable) return
      // Inside an editor that sits over the canvas (the step popup), keys belong to that editor.
      if (target?.closest?.('[data-canvas-shortcuts="off"]')) return
      if (event.key === 'Delete' || event.key === 'Backspace') onDelete()
      if ((event.ctrlKey || event.metaKey) && event.key === 'z') { event.preventDefault(); onUndo() }
      if ((event.ctrlKey || event.metaKey) && event.key === 'y') { event.preventDefault(); onRedo() }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onDelete, onUndo, onRedo])
}
