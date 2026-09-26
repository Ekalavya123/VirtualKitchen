import { useState } from 'react'
import '../styles/recipe-tool.css'
import '../process/styles/RecipePropertiesPanel.css'
import { RecipeDetailApi } from '../../../api'
import type { NutritionInfo } from '../../../types/recipe'
import { useNotifications } from '../../../shared/components/notifications/NotificationProvider'

type NutritionSectionProps = {
  recipeId: number
  nutrition: NutritionInfo | null | undefined
  isOwner: boolean
  onSaved: (nutrition: NutritionInfo | null) => void
}

type NutritionFormState = {
  calories: string
  proteinGrams: string
  carbohydratesGrams: string
  fatGrams: string
  fiberGrams: string
  sodiumMilligrams: string
  servings: string
}

const FIELDS: { key: keyof NutritionFormState; label: string; unit: string }[] = [
  { key: 'calories', label: 'Calories', unit: 'kcal' },
  { key: 'proteinGrams', label: 'Protein', unit: 'g' },
  { key: 'carbohydratesGrams', label: 'Carbohydrates', unit: 'g' },
  { key: 'fatGrams', label: 'Fat', unit: 'g' },
  { key: 'fiberGrams', label: 'Fiber', unit: 'g' },
  { key: 'sodiumMilligrams', label: 'Sodium', unit: 'mg' },
  { key: 'servings', label: 'Servings', unit: '' },
]

const toFormState = (nutrition: NutritionInfo | null | undefined): NutritionFormState => ({
  calories: nutrition?.calories?.toString() ?? '',
  proteinGrams: nutrition?.proteinGrams?.toString() ?? '',
  carbohydratesGrams: nutrition?.carbohydratesGrams?.toString() ?? '',
  fatGrams: nutrition?.fatGrams?.toString() ?? '',
  fiberGrams: nutrition?.fiberGrams?.toString() ?? '',
  sodiumMilligrams: nutrition?.sodiumMilligrams?.toString() ?? '',
  servings: nutrition?.servings?.toString() ?? '',
})

const toNutritionPayload = (form: NutritionFormState): NutritionInfo => ({
  calories: form.calories.trim() ? Number(form.calories) : undefined,
  proteinGrams: form.proteinGrams.trim() ? Number(form.proteinGrams) : undefined,
  carbohydratesGrams: form.carbohydratesGrams.trim() ? Number(form.carbohydratesGrams) : undefined,
  fatGrams: form.fatGrams.trim() ? Number(form.fatGrams) : undefined,
  fiberGrams: form.fiberGrams.trim() ? Number(form.fiberGrams) : undefined,
  sodiumMilligrams: form.sodiumMilligrams.trim() ? Number(form.sodiumMilligrams) : undefined,
  servings: form.servings.trim() ? Number(form.servings) : undefined,
})

export default function NutritionSection({ recipeId, nutrition, isOwner, onSaved }: NutritionSectionProps) {
  const { notifySuccess, notifyError } = useNotifications()
  const [form, setForm] = useState<NutritionFormState>(() => toFormState(nutrition))
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)

  // No effect re-syncing `form` from the `nutrition` prop: the initial useState(...) above already
  // covers first mount, and handleSave below re-derives `form` from the server's response the moment
  // save succeeds — the only other time `nutrition` changes.

  const hasAnyNutrition = FIELDS.some(({ key }) => nutrition?.[key] != null)

  if (!isOwner && !hasAnyNutrition) {
    return <div style={{ fontSize: 13, color: 'var(--flow-text-subtle)' }}>No nutrition information has been provided for this recipe.</div>
  }

  const handleChange = (key: keyof NutritionFormState, value: string) => {
    setForm((current) => ({ ...current, [key]: value }))
    setDirty(true)
  }

  const handleSave = async () => {
    setSaving(true)
    setSaveError(null)
    try {
      const updated = await RecipeDetailApi.updateNutrition(recipeId, toNutritionPayload(form))
      setForm(toFormState(updated.nutrition))
      onSaved(updated.nutrition ?? null)
      setDirty(false)
      notifySuccess('Nutrition saved')
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unable to save nutrition'
      setSaveError(message)
      notifyError(message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {FIELDS.map(({ key, label, unit }) => (
          <div key={key} className="flow-properties-field">
            <label className="flow-properties-label">{label}{unit ? ` (${unit})` : ''}</label>
            {isOwner ? (
              <input
                className="flow-properties-input"
                value={form[key]}
                onChange={(e) => handleChange(key, e.target.value)}
                placeholder="—"
                inputMode="decimal"
              />
            ) : (
              <div style={{ fontSize: 13, color: 'var(--flow-text)', fontWeight: 600 }}>
                {nutrition?.[key] != null ? String(nutrition[key]) : '—'}
              </div>
            )}
          </div>
        ))}
      </div>

      {isOwner && (
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleSave}
            disabled={!dirty || saving}
            style={{
              padding: '7px 16px', borderRadius: 8, border: '1px solid var(--flow-accent)',
              background: dirty ? 'var(--flow-accent)' : 'var(--flow-surface-muted)',
              color: dirty ? 'white' : 'var(--flow-text-muted)',
              fontSize: 12, fontWeight: 700, cursor: !dirty || saving ? 'default' : 'pointer',
            }}
          >
            {saving ? 'Saving…' : 'Save Nutrition'}
          </button>
          {dirty && !saving && <span style={{ fontSize: 11, color: 'var(--flow-warning)', fontWeight: 600 }}>● Unsaved changes</span>}
          {saveError && <span style={{ fontSize: 11, color: '#dc2626', fontWeight: 600 }}>{saveError}</span>}
        </div>
      )}
    </div>
  )
}
