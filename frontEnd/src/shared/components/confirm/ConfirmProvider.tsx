import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ConfirmContext, type ConfirmOptions } from './confirmContext'
import './ConfirmDialog.css'

type PendingConfirm = ConfirmOptions & { resolve: (confirmed: boolean) => void }

/**
 * Hosts the app's single confirmation dialog (same look as the recipe public/private prompt).
 * Components call {@link useConfirm} and await the user's answer.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null)

  const confirm = useCallback(
    (options: ConfirmOptions) =>
      new Promise<boolean>(resolve => {
        setPending(current => {
          // A second request while one is open cancels the first.
          current?.resolve(false)
          return { ...options, resolve }
        })
      }),
    [],
  )

  const settle = useCallback((confirmed: boolean) => {
    setPending(current => {
      current?.resolve(confirmed)
      return null
    })
  }, [])

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {pending && <ConfirmDialog options={pending} onSettle={settle} />}
    </ConfirmContext.Provider>
  )
}

function ConfirmDialog({ options, onSettle }: { options: ConfirmOptions; onSettle: (confirmed: boolean) => void }) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  const danger = (options.tone ?? 'danger') === 'danger'

  useEffect(() => {
    // Focus the safe choice, so a stray Enter never confirms a deletion.
    cancelRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        onSettle(false)
      }
    }
    window.addEventListener('keydown', onKeyDown, true)
    return () => window.removeEventListener('keydown', onKeyDown, true)
  }, [onSettle])

  return (
    <div
      className="confirm-dialog-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) onSettle(false)
      }}
    >
      <div
        className="confirm-dialog"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-message"
      >
        <div className="confirm-dialog-header">
          <div>
            {options.eyebrow && (
              <div className={`confirm-dialog-eyebrow${danger ? ' danger' : ''}`}>{options.eyebrow}</div>
            )}
            <h2 id="confirm-dialog-title" className="confirm-dialog-title">
              {options.title}
            </h2>
          </div>
          <button type="button" className="confirm-dialog-close" aria-label="Close" onClick={() => onSettle(false)}>
            ✕
          </button>
        </div>

        <p id="confirm-dialog-message" className="confirm-dialog-message">
          {options.message}
        </p>

        <div className="confirm-dialog-footer">
          <button ref={cancelRef} type="button" className="confirm-dialog-button secondary" onClick={() => onSettle(false)}>
            {options.cancelLabel ?? 'Cancel'}
          </button>
          <button
            type="button"
            className={`confirm-dialog-button ${danger ? 'danger' : 'primary'}`}
            onClick={() => onSettle(true)}
          >
            {options.confirmLabel ?? (danger ? 'Delete' : 'Confirm')}
          </button>
        </div>
      </div>
    </div>
  )
}
