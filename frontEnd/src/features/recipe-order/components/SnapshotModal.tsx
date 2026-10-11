import { useEffect, useRef } from 'react'

type SnapshotModalProps = {
  open: boolean
  onClose: () => void
  /** A step image from the order's recipe snapshot; an illustrative drawing is shown without one. */
  imageUrl?: string
}

/** Inline placeholder art: a pot on a stove — clearly a drawing, not a photo. */
function SnapshotPlaceholder() {
  return (
    <svg className="ro-snapshot-svg" viewBox="0 0 320 200" role="img" aria-label="Illustration of a pot on a stove">
      <rect width="320" height="200" fill="var(--flow-accent-soft)" />
      <rect x="40" y="150" width="240" height="14" rx="7" fill="var(--flow-border-strong)" />
      <path d="M95 150 Q100 98 110 92 L210 92 Q220 98 225 150 Z" fill="var(--flow-accent)" opacity="0.85" />
      <rect x="100" y="84" width="120" height="12" rx="6" fill="var(--flow-accent-strong)" />
      <rect x="72" y="100" width="28" height="8" rx="4" fill="var(--flow-accent-strong)" />
      <rect x="220" y="100" width="28" height="8" rx="4" fill="var(--flow-accent-strong)" />
      <path d="M130 70 q-8 -14 0 -28 M160 66 q-8 -14 0 -28 M190 70 q-8 -14 0 -28" stroke="var(--flow-text-muted)" strokeWidth="4" fill="none" strokeLinecap="round" opacity="0.6" />
      <path d="M120 164 q8 12 16 0 q8 12 16 0 M168 164 q8 12 16 0 q8 12 16 0" stroke="var(--flow-warning)" strokeWidth="4" fill="none" strokeLinecap="round" />
    </svg>
  )
}

/**
 * "Request snapshot" (an intended future feature, UI only): explains the feature and shows an
 * illustrative preview, clearly badged as not being a live kitchen image. A modal dialog — focus
 * moves into it, Tab stays inside, Esc closes, and focus returns to what opened it.
 */
export default function SnapshotModal({ open, onClose, imageUrl }: SnapshotModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  // Read through a ref so a new onClose from the parent doesn't re-run the focus effect below.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    closeRef.current?.focus()

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onCloseRef.current()
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = dialogRef.current.querySelectorAll<HTMLElement>('button, [href], [tabindex]:not([tabindex="-1"])')
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      previouslyFocused?.focus()
    }
  }, [open])

  if (!open) return null

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div ref={dialogRef} className="recipe-modal ro-snapshot-modal" role="dialog" aria-modal="true" aria-labelledby="ro-snapshot-title" aria-describedby="ro-snapshot-text">
        <div className="ro-snapshot-header">
          <h2 id="ro-snapshot-title">Preparation snapshot</h2>
          <button ref={closeRef} type="button" className="ro-icon-btn" onClick={onClose} aria-label="Close">
            ✕
          </button>
        </div>
        <div className="ro-snapshot-body">
          <p id="ro-snapshot-text">
            Preparation snapshots will allow you to request an image of the recipe being prepared in the physical kitchen. In this
            prototype, the feature is for demonstration only. The displayed preview is illustrative and does not show a live kitchen.
          </p>
          <figure className="ro-snapshot-figure">
            {imageUrl ? <img src={imageUrl} alt="Illustrative preview of this recipe step" /> : <SnapshotPlaceholder />}
            <figcaption className="ro-snapshot-badges">
              <span className="ro-badge ro-badge-accent">Illustrative preview</span>
              <span className="ro-badge ro-badge-warning">Not a live kitchen image</span>
              <span className="ro-badge ro-badge-neutral">Intended future feature</span>
            </figcaption>
          </figure>
        </div>
        <div className="ro-snapshot-footer">
          <button type="button" className="ro-btn ro-btn-primary" onClick={onClose}>
            Got it
          </button>
        </div>
      </div>
    </div>
  )
}
