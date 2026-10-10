type ServingsStepperProps = {
  value: number
  /** Null when the recipe has no serving count: the order is then counted in batches. */
  baseServings: number | null
  onChange: (value: number) => void
  saving?: boolean
  disabled?: boolean
  max?: number
}

/** − / + control for the order's servings; each change is saved straight away by the caller. */
export default function ServingsStepper({ value, baseServings, onChange, saving, disabled, max = 50 }: ServingsStepperProps) {
  const unit = baseServings == null ? (value === 1 ? 'batch' : 'batches') : value === 1 ? 'serving' : 'servings'
  const busy = disabled || saving

  return (
    <div className="ro-servings">
      <div className="ro-servings-control" role="group" aria-label={baseServings == null ? 'Batches' : 'Servings'}>
        <button type="button" className="ro-servings-btn" onClick={() => onChange(value - 1)} disabled={busy || value <= 1} aria-label="Fewer">
          −
        </button>
        <span className="ro-servings-value" aria-live="polite">
          <strong>{value}</strong> {unit}
        </span>
        <button type="button" className="ro-servings-btn" onClick={() => onChange(value + 1)} disabled={busy || value >= max} aria-label="More">
          +
        </button>
      </div>
      <p className="ro-hint">
        {saving ? 'Saving…' : baseServings == null
          ? 'This recipe has no serving count, so you order whole batches of it.'
          : `The recipe makes ${baseServings} ${baseServings === 1 ? 'serving' : 'servings'}.`}{' '}
        Ingredient quantities are scaled server-side.
      </p>
    </div>
  )
}
