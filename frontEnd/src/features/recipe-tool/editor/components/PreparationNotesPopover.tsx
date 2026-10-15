import { useState } from 'react'
import InfoPopover from '../../../../shared/components/InfoPopover'
import { errorMessage } from '../../../../shared/errors'
import { DEFAULT_PREPARATION_NOTES, effectivePreparationNotes } from '../../model/preparationNotes'

type PreparationNotesPopoverProps = {
  /** The recipe's own guidance; null for the default. */
  notes: string | null
  /** Saves new guidance (null = back to the default); omitted for readers. */
  onSave?: (notes: string | null) => Promise<void>
}

/**
 * The ⓘ next to the Preparation heading: the routine handling every preparation assumes (washing, peeling, patting
 * dry), so steps needn't repeat it. The owner can rewrite it for this recipe or go back to the default.
 */
export default function PreparationNotesPopover({ notes, onSave }: PreparationNotesPopoverProps) {
  const [draft, setDraft] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const custom = notes != null && notes.trim() !== ''

  const save = async (value: string | null, close: () => void) => {
    if (!onSave) return
    setSaving(true)
    setError(null)
    try {
      await onSave(value && value.trim() && value.trim() !== DEFAULT_PREPARATION_NOTES ? value.trim() : null)
      setDraft(null)
      close()
    } catch (caught) {
      setError(errorMessage(caught, 'Unable to save the notes'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <InfoPopover label="Default preparation notes">
      {(close) => (
        <>
          <span style={{ fontWeight: 700 }}>Default preparation notes{custom ? '' : ' (standard)'}</span>
          <span style={{ color: 'var(--flow-text-muted)' }}>
            Routine handling every preparation assumes, so steps don't need to repeat it. Anything specific still goes in a step.
          </span>
          {draft == null ? (
            <>
              <span style={{ whiteSpace: 'pre-line' }}>{effectivePreparationNotes(notes)}</span>
              {onSave && (
                <span style={{ display: 'flex', gap: 6 }}>
                  <button type="button" className="recipe-topbar-button" style={{ height: 26, padding: '0 10px' }}
                    onClick={() => setDraft(effectivePreparationNotes(notes))}>
                    Edit
                  </button>
                  {custom && (
                    <button type="button" className="recipe-topbar-button" style={{ height: 26, padding: '0 10px' }}
                      disabled={saving} onClick={() => void save(null, close)}>
                      Use default
                    </button>
                  )}
                </span>
              )}
            </>
          ) : (
            <>
              <textarea
                className="flow-properties-textarea"
                rows={6}
                value={draft}
                maxLength={4000}
                onChange={(event) => setDraft(event.target.value)}
                aria-label="Preparation notes for this recipe"
              />
              {error && <span style={{ color: '#dc2626', fontWeight: 600 }}>{error}</span>}
              <span style={{ display: 'flex', gap: 6 }}>
                <button type="button" className="recipe-topbar-button" style={{ height: 26, padding: '0 10px' }}
                  disabled={saving} onClick={() => void save(draft, close)}>
                  {saving ? 'Saving…' : 'Save'}
                </button>
                <button type="button" className="recipe-topbar-button" style={{ height: 26, padding: '0 10px' }}
                  disabled={saving} onClick={() => setDraft(null)}>
                  Cancel
                </button>
              </span>
            </>
          )}
        </>
      )}
    </InfoPopover>
  )
}
