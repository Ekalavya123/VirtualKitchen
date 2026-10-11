import { useEffect, useRef, useState } from 'react'
import './ActionMenu.css'

export type ActionMenuItem = {
  label: string
  onSelect: () => void
  danger?: boolean
}

type ActionMenuProps = {
  items: ActionMenuItem[]
  /** Accessible name of the "⋯" button, e.g. "More actions for Paneer Masala". */
  label: string
  /** Which way the menu opens from its button. */
  placement?: 'down' | 'up'
  className?: string
}

/** A "⋯" button with a small menu: closes on choosing an item, clicking elsewhere, or Escape. */
export default function ActionMenu({ items, label, placement = 'down', className = '' }: ActionMenuProps) {
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  if (items.length === 0) return null

  return (
    <div ref={menuRef} className={`action-menu${open ? ' open' : ''} ${className}`.trim()}>
      <button
        type="button"
        className="action-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        title="More actions"
        onClick={() => setOpen(current => !current)}
      >
        ⋯
      </button>

      {open && (
        <div className={`action-menu-popover ${placement}`} role="menu">
          {items.map(item => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              className={`action-menu-item${item.danger ? ' danger' : ''}`}
              onClick={() => {
                setOpen(false)
                item.onSelect()
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
