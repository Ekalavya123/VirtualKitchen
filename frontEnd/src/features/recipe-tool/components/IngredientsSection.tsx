import { useMemo } from 'react'
import '../styles/recipe-tool.css'
import '../process/styles/RecipePropertiesPanel.css'
import type { UnitType } from '../../../types/recipe'
import { normalizeRecipeStepNodeData, type ActionOnIngredient } from '../process/model/recipeStepData'
import { getIngredientById } from '../catalog/ingredientCatalog'
import { getPreparationStyleDisplayName } from '../catalog/preparationStyleCatalog'
import { useRecipeSession } from '../context/RecipeSessionContext'

const UNIT_LABELS: Record<UnitType, string> = {
  COUNT: 'count',
  GRAM: 'g',
  KG: 'kg',
  ML: 'mL',
  LITER: 'L',
}

type DerivedIngredient = ActionOnIngredient & { preparations: string[] }

/** Sums Action On ingredient usage across every STEP node in every process (MAIN + subprocesses) belonging to this recipe, grouped by ingredient + unit. */
const deriveIngredientsFromProcesses = (
  processes: { nodes: { kind: string; data?: Record<string, unknown> }[] }[],
): DerivedIngredient[] => {
  const byKey = new Map<string, DerivedIngredient>()

  for (const process of processes) {
    for (const node of process.nodes) {
      if (node.kind !== 'STEP') continue
      const { step } = normalizeRecipeStepNodeData(node.data)
      for (const usage of step.actionOn.ingredients) {
        const key = `${usage.ingredientId}:${usage.unit}`
        const preparationLabel = getPreparationStyleDisplayName(usage.preparationStyleId ?? '', usage.customPreparationStyle)
        const existing = byKey.get(key)
        if (existing) {
          existing.quantity += usage.quantity
          if (preparationLabel && !existing.preparations.includes(preparationLabel)) {
            existing.preparations.push(preparationLabel)
          }
        } else {
          byKey.set(key, { ...usage, preparations: preparationLabel ? [preparationLabel] : [] })
        }
      }
    }
  }

  return Array.from(byKey.values())
}

/**
 * Read-only "what does this recipe actually use" view: ingredients are
 * derived from every step's Action On configuration across the recipe's
 * whole process tree (Recipe Process is the source of truth), not a
 * separately maintained list. There is no "Add Ingredient" control here on
 * purpose — using an ingredient happens by adding it to a step's Action On
 * in the Recipe Process tab.
 *
 * Reads directly from RecipeSessionContext — the same unified in-memory
 * recipe snapshot RecipeProcessView/RecipeProcessCanvas read and write — so an
 * unsaved Action On edit to any process (MAIN or any subprocess, including
 * one the user isn't currently looking at) shows up here immediately,
 * without requiring a save first and without a separate fetch of its own.
 */
export default function IngredientsSection() {
  const session = useRecipeSession()

  // Re-derives whenever `session` changes identity — which RecipeSessionContext guarantees
  // happens on every process edit (its `version` bumps) or when loading completes, not only when
  // the processes array itself is replaced wholesale.
  const derived = useMemo(() => {
    if (!session || session.loading) return null
    return deriveIngredientsFromProcesses(session.getProcesses())
  }, [session])

  if (session?.loadError) {
    return <div style={{ fontSize: 13, color: '#9f1239' }}>{session.loadError}</div>
  }

  if (derived === null) {
    return <div style={{ fontSize: 13, color: 'var(--flow-text-subtle)' }}>Loading ingredients…</div>
  }

  return (
    <div className="flex flex-col gap-3">
      <div
        style={{ fontSize: 12, color: 'var(--flow-text-muted)', background: 'var(--flow-surface-muted)', border: '1px solid var(--flow-border)', borderRadius: 8, padding: '8px 10px' }}
      >
        🧩 Ingredients are automatically derived from your recipe process. To change what's used, edit a step's Action On in the Recipe Process tab.
      </div>

      {derived.length === 0 ? (
        <div style={{ fontSize: 13, color: 'var(--flow-text-subtle)' }}>
          No ingredients yet — add ingredients to a step's Action On in the Recipe Process tab.
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {derived.map((entry, index) => {
            const catalogEntry = getIngredientById(entry.ingredientId)
            return (
              <div
                key={`${entry.ingredientId}-${entry.unit}-${index}`}
                className="flex flex-wrap items-center gap-3 rounded-lg border p-2.5"
                style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface)' }}
              >
                <div
                  style={{
                    width: 40, height: 40, borderRadius: 8, flexShrink: 0,
                    background: 'var(--flow-surface-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <span aria-hidden style={{ fontSize: 20 }}>{catalogEntry.icon}</span>
                </div>

                <div style={{ minWidth: 120, fontWeight: 700, fontSize: 13, color: 'var(--flow-text)' }}>
                  {catalogEntry.name}
                </div>

                <div style={{ fontSize: 12, color: 'var(--flow-text-muted)', fontWeight: 600 }}>
                  {entry.quantity} {UNIT_LABELS[entry.unit]}
                </div>

                {entry.preparations.length > 0 && (
                  <div style={{ fontSize: 11.5, color: 'var(--flow-text-subtle)' }}>· {entry.preparations.join(', ')}</div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
