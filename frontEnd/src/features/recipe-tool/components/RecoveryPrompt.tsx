import '../styles/recipe-tool.css'

type RecoveryPromptProps = {
  /** When the unsaved changes were last written locally (epoch ms). */
  writtenAt: number
  /** The recipe was saved (elsewhere, or by a later session) after these changes were made. */
  backendChanged: boolean
  onRecover: () => void
  onDiscard: () => void
}

const formatWhen = (writtenAt: number) => {
  const date = new Date(writtenAt)
  const sameDay = date.toDateString() === new Date().toDateString()
  return sameDay
    ? `today at ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
    : date.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}

/**
 * Shown by RecipeSessionProvider when this recipe was reopened with unsaved changes left over from
 * a refresh, crash or closed tab (see persistence/recoveryStore.ts). Blocks the editor until the
 * user decides, so nothing is silently overwritten either way.
 */
export default function RecoveryPrompt({ writtenAt, backendChanged, onRecover, onDiscard }: RecoveryPromptProps) {
  return (
    <div className="flow-canvas-export-modal-overlay" role="presentation">
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="recipe-recovery-title"
        aria-describedby="recipe-recovery-description"
        style={{
          width: 'min(440px, calc(100vw - 32px))', padding: '20px 22px', borderRadius: 14,
          background: 'var(--flow-surface)', border: '1px solid var(--flow-border)',
          boxShadow: '0 18px 48px rgba(0,0,0,0.18)', display: 'flex', flexDirection: 'column', gap: 12,
        }}
      >
        <div id="recipe-recovery-title" style={{ fontSize: 15, fontWeight: 700, color: 'var(--flow-text)' }}>
          Unsaved changes were found
        </div>
        <div id="recipe-recovery-description" style={{ fontSize: 12.5, lineHeight: 1.55, color: 'var(--flow-text-muted)' }}>
          Changes you made {formatWhen(writtenAt)} weren't saved before the page was closed or reloaded.
          Recover them to keep editing where you left off, or discard them to keep the saved recipe.
          {backendChanged && (
            <div style={{ marginTop: 8, padding: '8px 10px', borderRadius: 8, background: 'var(--flow-warning-soft)', border: '1px solid var(--flow-warning-border)', color: 'var(--flow-warning)', fontWeight: 600 }}>
              The saved recipe has changed since then. Recovering will replace those newer changes.
            </div>
          )}
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 4 }}>
          <button
            type="button"
            onClick={onDiscard}
            style={{ padding: '8px 14px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', color: 'var(--flow-text-muted)', fontSize: 12.5, fontWeight: 700, cursor: 'pointer' }}
          >
            Discard changes
          </button>
          <button
            type="button"
            autoFocus
            onClick={onRecover}
            style={{ padding: '8px 14px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12.5, fontWeight: 700, cursor: 'pointer' }}
          >
            Recover changes
          </button>
        </div>
      </div>
    </div>
  )
}
