import { useState } from 'react'
import type { NutritionInfo } from '../../../../types/recipe'
import NutritionEditor from '../../components/NutritionEditor'

type RecipeNutritionPanelProps = {
  recipeId: number
  nutrition: NutritionInfo | null | undefined
  isOwner: boolean
  onSaved: (nutrition: NutritionInfo | null) => void
}

type NutrientKey = Exclude<keyof NutritionInfo, 'servings'>

const NUTRIENTS: { key: NutrientKey; label: string; unit: string; color?: string }[] = [
  { key: 'calories', label: 'Calories', unit: 'kcal' },
  { key: 'proteinGrams', label: 'Protein', unit: 'g', color: 'var(--rp-macro-protein)' },
  { key: 'carbohydratesGrams', label: 'Carbs', unit: 'g', color: 'var(--rp-macro-carbs)' },
  { key: 'fatGrams', label: 'Fat', unit: 'g', color: 'var(--rp-macro-fat)' },
  { key: 'fiberGrams', label: 'Fiber', unit: 'g' },
  { key: 'sodiumMilligrams', label: 'Sodium', unit: 'mg' },
]

// Energy per gram, used only to show how the stated macros split the calories.
const MACROS: { key: 'proteinGrams' | 'carbohydratesGrams' | 'fatGrams'; label: string; kcalPerGram: number; color: string }[] = [
  { key: 'proteinGrams', label: 'Protein', kcalPerGram: 4, color: 'var(--rp-macro-protein)' },
  { key: 'carbohydratesGrams', label: 'Carbs', kcalPerGram: 4, color: 'var(--rp-macro-carbs)' },
  { key: 'fatGrams', label: 'Fat', kcalPerGram: 9, color: 'var(--rp-macro-fat)' },
]

const formatNumber = (value: number) => (Number.isInteger(value) ? String(value) : value.toFixed(1))

/**
 * Nutrition as the last part of the recipe: only values that were actually entered are shown —
 * never zeros or placeholders for missing ones. The owner edits it in place.
 */
export default function RecipeNutritionPanel({ recipeId, nutrition, isOwner, onSaved }: RecipeNutritionPanelProps) {
  const [editing, setEditing] = useState(false)

  const present = NUTRIENTS.filter(({ key }) => nutrition?.[key] != null)
  const macros = MACROS
    .map((macro) => ({ ...macro, kcal: (nutrition?.[macro.key] ?? 0) * macro.kcalPerGram }))
    .filter((macro) => macro.kcal > 0)
  const macroTotal = macros.reduce((sum, macro) => sum + macro.kcal, 0)

  if (editing) {
    return (
      <div className="rp-nutrition-card">
        <NutritionEditor
          recipeId={recipeId}
          nutrition={nutrition}
          onCancel={() => setEditing(false)}
          onSaved={(saved) => {
            onSaved(saved)
            setEditing(false)
          }}
        />
      </div>
    )
  }

  if (present.length === 0) {
    return (
      <div className="rp-empty-card">
        <span className="rp-empty-card-icon" aria-hidden>📊</span>
        Nutrition information hasn't been added for this recipe.
        {isOwner && (
          <button type="button" className="rp-button" onClick={() => setEditing(true)}>Add nutrition</button>
        )}
      </div>
    )
  }

  return (
    <div className="rp-nutrition-card">
      <dl className="rp-nutrition-grid">
        {present.map(({ key, label, unit, color }) => (
          <div key={key} className={`rp-nutrient${key === 'calories' ? ' is-calories' : ''}`}>
            <dt>
              {color && <span className="rp-dot" style={{ background: color }} aria-hidden />}
              {label}
            </dt>
            <dd>
              {formatNumber(nutrition?.[key] as number)}
              <small>{unit}</small>
            </dd>
          </div>
        ))}
      </dl>

      {macros.length >= 2 && (
        <div className="rp-macro">
          <span className="rp-macro-title">Where the calories come from</span>
          <div className="rp-macro-bar" role="img" aria-label={macros.map((macro) => `${macro.label} ${Math.round((macro.kcal / macroTotal) * 100)}%`).join(', ')}>
            {macros.map((macro) => (
              <span key={macro.key} style={{ width: `${(macro.kcal / macroTotal) * 100}%`, background: macro.color }} />
            ))}
          </div>
          <div className="rp-macro-legend">
            {macros.map((macro) => (
              <span key={macro.key}>
                <span className="rp-dot" style={{ background: macro.color }} aria-hidden />
                {macro.label} <strong>{Math.round((macro.kcal / macroTotal) * 100)}%</strong>
              </span>
            ))}
          </div>
        </div>
      )}

      {isOwner && (
        <div>
          <button type="button" className="rp-button rp-button-ghost" onClick={() => setEditing(true)}>✏️ Edit nutrition</button>
        </div>
      )}
    </div>
  )
}
