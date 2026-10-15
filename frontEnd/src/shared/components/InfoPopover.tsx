import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import './InfoPopover.css'

type InfoPopoverProps = {
  /** Accessible name of the ⓘ button, e.g. "Default preparation notes". */
  label: string
  /** The panel's content; a function gets `close` (e.g. for a Save button inside). */
  children: ReactNode | ((close: () => void) => ReactNode)
}

const PANEL_WIDTH = 300
const VIEWPORT_MARGIN = 8

/**
 * A small ⓘ button that opens a panel of guidance on click (not hover, so the panel can hold an editable form).
 * The panel is positioned against the viewport, so a narrow or scrolling container (a sidebar) never clips it.
 * Closes on Escape or a click outside.
 */
export default function InfoPopover({ label, children }: InfoPopoverProps) {
  const [position, setPosition] = useState<CSSProperties | null>(null)
  const rootRef = useRef<HTMLSpanElement | null>(null)
  const buttonRef = useRef<HTMLButtonElement | null>(null)
  const panelId = useId()
  const open = position != null

  useEffect(() => {
    if (!open) return undefined
    const close = () => setPosition(null)
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) close()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close()
    }
    // Scrolling the page or a container would leave the panel behind its button: close it instead
    // (scrolling inside the panel itself, e.g. its textarea, is fine).
    const onScroll = (event: Event) => {
      if (!rootRef.current?.contains(event.target as Node)) close()
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    window.addEventListener('resize', close)
    window.addEventListener('scroll', onScroll, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('resize', close)
      window.removeEventListener('scroll', onScroll, true)
    }
  }, [open])

  const toggle = () => {
    if (open || !buttonRef.current) {
      setPosition(null)
      return
    }
    const rect = buttonRef.current.getBoundingClientRect()
    const width = Math.min(PANEL_WIDTH, window.innerWidth - 2 * VIEWPORT_MARGIN)
    const left = Math.max(VIEWPORT_MARGIN, Math.min(rect.left - 6, window.innerWidth - width - VIEWPORT_MARGIN))
    setPosition({ top: rect.bottom + 6, left, width })
  }

  const close = () => setPosition(null)
  return (
    <span ref={rootRef} className="info-popover">
      <button
        ref={buttonRef}
        type="button"
        className="info-popover-button"
        aria-label={label}
        title={label}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={toggle}
      >
        ⓘ
      </button>
      {position && (
        <span id={panelId} role="dialog" aria-label={label} className="info-popover-panel" style={position}>
          {typeof children === 'function' ? children(close) : children}
        </span>
      )}
    </span>
  )
}
