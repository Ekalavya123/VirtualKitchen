import { useState } from 'react'
import '../../flow-editor/styles/flow-editor.css'
import '../../flow-editor/components/toolbar/PropertiesPanel.css'
import SearchableSelect, { type SearchableSelectOption } from '../../../shared/components/SearchableSelect'
import type { GlobalIngredient } from '../../../api'
import type { RecipeIngredient, UnitType } from '../../../types/process'

const UNIT_OPTIONS: { value: UnitType; label: string }[] = [
  { value: 'COUNT', label: 'count' },
  { value: 'GRAM', label: 'g' },
  { value: 'KG', label: 'kg' },
  { value: 'ML', label: 'mL' },
  { value: 'LITER', label: 'L' },
]

type IngredientSelectorProps = {
  /** The global ingredient catalog, already loaded by the caller — this component is purely a picker, not a data source. */
  catalog: GlobalIngredient[]
  /** Ingredient ids already on the recipe, excluded from the picker so the same ingredient isn't added twice. */
  excludeIngredientIds: number[]
  onAdd: (ingredient: RecipeIngredient) => void
}

/**
 * Small "add an ingredient" row: pick an existing global ingredient (never a
 * new one — the Recipe Tool has no ingredient-catalog-management UI, only
 * IngredientController's existing catalog), then set this recipe's own
 * quantity/unit/notes/preparation for it.
 */
export default function IngredientSelector({ catalog, excludeIngredientIds, onAdd }: IngredientSelectorProps) {
  const [ingredientIdValue, setIngredientIdValue] = useState('')
  const [quantity, setQuantity] = useState('')
  const [unit, setUnit] = useState<UnitType>('COUNT')
  const [notes, setNotes] = useState('')
  const [preparation, setPreparation] = useState('')
  const [error, setError] = useState<string | null>(null)

  const availableCatalog = catalog.filter((item) => !excludeIngredientIds.includes(item.id))
  const options: SearchableSelectOption[] = availableCatalog.map((item) => ({ value: String(item.id), label: item.name }))

  const handleAdd = () => {
    const ingredientId = Number(ingredientIdValue)
    if (!ingredientIdValue || !Number.isFinite(ingredientId)) {
      setError('Choose an ingredient')
      return
    }

    const quantityNumber = Number(quantity)
    if (!quantity.trim() || !Number.isFinite(quantityNumber) || quantityNumber < 0) {
      setError('Enter a valid quantity')
      return
    }

    onAdd({
      ingredientId,
      quantity: quantityNumber,
      unit,
      notes: notes.trim() || undefined,
      preparation: preparation.trim() || undefined,
    })

    setIngredientIdValue('')
    setQuantity('')
    setUnit('COUNT')
    setNotes('')
    setPreparation('')
    setError(null)
  }

  return (
    <div className="flex flex-col gap-2 rounded-xl border p-3" style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface-muted)' }}>
      <div className="flex flex-wrap items-end gap-2">
        <div style={{ minWidth: 200, flex: 1 }}>
          <label className="flow-properties-label">Ingredient</label>
          <SearchableSelect
            value={ingredientIdValue}
            onChange={setIngredientIdValue}
            options={options}
            placeholder={availableCatalog.length ? 'Select an ingredient' : 'No more ingredients in the catalog'}
          />
        </div>
        <div style={{ width: 90 }}>
          <label className="flow-properties-label">Quantity</label>
          <input className="flow-properties-input" value={quantity} onChange={(e) => setQuantity(e.target.value)} placeholder="2" />
        </div>
        <div style={{ width: 90 }}>
          <label className="flow-properties-label">Unit</label>
          <select className="flow-properties-input" value={unit} onChange={(e) => setUnit(e.target.value as UnitType)}>
            {UNIT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex flex-wrap gap-2">
        <input
          className="flow-properties-input"
          style={{ flex: 1, minWidth: 160 }}
          value={preparation}
          onChange={(e) => setPreparation(e.target.value)}
          placeholder="Preparation (e.g. diced) — optional"
        />
        <input
          className="flow-properties-input"
          style={{ flex: 1, minWidth: 160 }}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes — optional"
        />
      </div>

      {error && <div style={{ fontSize: 12, color: '#dc2626', fontWeight: 600 }}>{error}</div>}

      <button
        type="button"
        onClick={handleAdd}
        style={{ alignSelf: 'flex-start', padding: '6px 14px', borderRadius: 8, border: '1px solid var(--flow-accent)', background: 'var(--flow-accent)', color: 'white', fontSize: 12, fontWeight: 700, cursor: 'pointer' }}
      >
        + Add Ingredient
      </button>
    </div>
  )
}
