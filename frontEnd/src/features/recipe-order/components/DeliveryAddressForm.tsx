import type { DeliveryAddress } from '../../../types/recipeOrder'
import type { RequiredAddressField } from '../model/orderFormat'

type DeliveryAddressFormProps = {
  value: DeliveryAddress
  onChange: (value: DeliveryAddress) => void
  /** Required fields to flag (shown once the user has tried to continue). */
  missing: readonly RequiredAddressField[]
  disabled?: boolean
}

const FIELDS: { key: keyof DeliveryAddress; label: string; autoComplete: string; required?: boolean; wide?: boolean }[] = [
  { key: 'recipientName', label: 'Recipient name', autoComplete: 'name', required: true, wide: true },
  { key: 'line1', label: 'Address line 1', autoComplete: 'address-line1', required: true, wide: true },
  { key: 'line2', label: 'Address line 2', autoComplete: 'address-line2', wide: true },
  { key: 'city', label: 'City', autoComplete: 'address-level2', required: true },
  { key: 'state', label: 'State / region', autoComplete: 'address-level1' },
  { key: 'postalCode', label: 'Postal code', autoComplete: 'postal-code', required: true },
  { key: 'phone', label: 'Phone', autoComplete: 'tel' },
]

/** The order's delivery address (prefilled from the saved default by the backend). */
export default function DeliveryAddressForm({ value, onChange, missing, disabled }: DeliveryAddressFormProps) {
  return (
    <div className="ro-form-grid">
      {FIELDS.map((field) => {
        const id = `ro-address-${field.key}`
        const invalid = field.required && missing.includes(field.key as RequiredAddressField)
        return (
          <div key={field.key} className={`ro-field${field.wide ? ' ro-field-wide' : ''}`}>
            <label htmlFor={id}>
              {field.label}
              {field.required && <span className="ro-required" aria-hidden> *</span>}
            </label>
            <input
              id={id}
              className="ro-input"
              value={value[field.key] ?? ''}
              onChange={(event) => onChange({ ...value, [field.key]: event.target.value })}
              autoComplete={field.autoComplete}
              type={field.key === 'phone' ? 'tel' : 'text'}
              required={field.required}
              aria-invalid={invalid || undefined}
              aria-describedby={invalid ? `${id}-error` : undefined}
              disabled={disabled}
            />
            {invalid && (
              <span id={`${id}-error`} className="ro-field-error">
                Required
              </span>
            )}
          </div>
        )
      })}
    </div>
  )
}
