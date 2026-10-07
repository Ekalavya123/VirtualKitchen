/**
 * One quiet line telling the user a form was refilled from what they typed on an earlier visit,
 * with a way to start over. Uses the Recipe Tool's CSS variables, with neutral fallbacks elsewhere.
 */
export default function DraftRestoredNote({ onDiscard, label = 'Restored what you typed last time.' }: {
  onDiscard: () => void
  label?: string
}) {
  return (
    <div role="status" style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11.5, color: 'var(--flow-text-subtle, #94a3b8)' }}>
      <span>↺ {label}</span>
      <button
        type="button"
        onClick={onDiscard}
        style={{ border: 'none', background: 'none', padding: 0, color: 'var(--flow-accent-strong, #4f46e5)', fontSize: 11.5, fontWeight: 700, cursor: 'pointer' }}
      >
        Clear
      </button>
    </div>
  )
}
