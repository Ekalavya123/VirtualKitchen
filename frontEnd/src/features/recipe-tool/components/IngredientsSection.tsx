import { useEffect, useMemo, useState } from 'react'
import '../../flow-editor/styles/flow-editor.css'
import '../../flow-editor/components/toolbar/PropertiesPanel.css'
import { IngredientCatalogApi, ProcessApi, type GlobalIngredient } from '../../../api'
import type { Process, RecipeIngredient, UnitType } from '../../../types/process'
import { normalizeProcessStepNodeData } from '../../flow-editor/model/processStepData'
import { useProcessLiveGraph } from '../../flow-editor/context/ProcessLiveGraphContext'

type IngredientsSectionProps = {
  recipeId: number
}

const UNIT_LABELS: Record<UnitType, string> = {
  COUNT: 'count',
  GRAM: 'g',
  KG: 'kg',
  ML: 'mL',
  LITER: 'L',
}

type DerivedIngredient = RecipeIngredient & { preparations: string[] }

/** Sums Action On ingredient usage across every STEP node in every process (MAIN + subprocesses) belonging to this recipe, grouped by ingredient + unit. */
const deriveIngredientsFromProcesses = (
  processes: { nodes: { kind: string; data?: Record<string, unknown> }[] }[],
): DerivedIngredient[] => {
  const byKey = new Map<string, DerivedIngredient>()

  for (const process of processes) {
    for (const node of process.nodes) {
      if (node.kind !== 'STEP') continue
      const { step } = normalizeProcessStepNodeData(node.data)
      for (const usage of step.actionOn.ingredients) {
        const key = `${usage.ingredientId}:${usage.unit}`
        const existing = byKey.get(key)
        if (existing) {
          existing.quantity += usage.quantity
          if (usage.preparation && !existing.preparations.includes(usage.preparation)) {
            existing.preparations.push(usage.preparation)
          }
        } else {
          byKey.set(key, { ...usage, preparations: usage.preparation ? [usage.preparation] : [] })
        }
      }
    }
  }

  return Array.from(byKey.values())
}

/**
 * Read-only "what does this recipe actually use" view: ingredients are
 * derived from every step's Action On configuration across the recipe's
 * whole process tree (Recipe Process is the source of truth — see the
 * brief's data-flow diagram), not a separately maintained list. There is no
 * "Add Ingredient" control here on purpose — using an ingredient happens by
 * adding it to a step's Action On in the Recipe Process tab.
 *
 * Derivation prefers each process's *live, in-memory* nodes (from
 * ProcessLiveGraphContext, published by ProcessCanvas as the user edits)
 * over the last-saved copy from `ProcessApi.listByRecipe` — so an unsaved
 * Action On edit, a newly added/removed step, or an edit made to a
 * different subprocess earlier in this visit shows up here immediately,
 * without requiring a save first. The server fetch still supplies the list
 * of processes to consider (and is the fallback for any process not yet
 * opened this visit).
 */
export default function IngredientsSection({ recipeId }: IngredientsSectionProps) {
  const liveGraph = useProcessLiveGraph()
  const [catalog, setCatalog] = useState<GlobalIngredient[]>([])
  const [processes, setProcesses] = useState<Process[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    Promise.all([IngredientCatalogApi.list(), ProcessApi.listByRecipe(recipeId)])
      .then(([catalogResult, processList]) => {
        if (cancelled) return
        setCatalog(catalogResult)
        setProcesses(processList)
        setLoadError(null)
      })
      .catch((error) => {
        if (!cancelled) setLoadError(error instanceof Error ? error.message : 'Unable to load ingredients')
      })
    return () => {
      cancelled = true
    }
  }, [recipeId])

  const catalogById = useMemo(() => new Map(catalog.map((item) => [item.id, item])), [catalog])

  // Re-derives whenever the live store changes (liveGraph's identity changes on every
  // setLiveNodes call — see ProcessLiveGraphContext) or the server-loaded process list changes.
  const derived = useMemo(() => {
    if (processes === null) return null
    const effectiveProcesses = processes.map((process) => ({
      nodes: liveGraph?.getLiveNodes(process.id) ?? process.nodes,
    }))
    return deriveIngredientsFromProcesses(effectiveProcesses)
  }, [processes, liveGraph])

  if (loadError) {
    return <div style={{ fontSize: 13, color: '#9f1239' }}>{loadError}</div>
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
            const catalogEntry = catalogById.get(entry.ingredientId)
            return (
              <div
                key={`${entry.ingredientId}-${entry.unit}-${index}`}
                className="flex flex-wrap items-center gap-3 rounded-lg border p-2.5"
                style={{ border: '1px solid var(--flow-border)', background: 'var(--flow-surface)' }}
              >
                <div
                  style={{
                    width: 40, height: 40, borderRadius: 8, flexShrink: 0, overflow: 'hidden',
                    background: 'var(--flow-surface-muted)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  {catalogEntry?.imageUrl ? (
                    <img src={catalogEntry.imageUrl} alt={catalogEntry.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <span aria-hidden style={{ fontSize: 16, opacity: 0.5 }}>🥕</span>
                  )}
                </div>

                <div style={{ minWidth: 120, fontWeight: 700, fontSize: 13, color: 'var(--flow-text)' }}>
                  {catalogEntry?.name ?? `Ingredient #${entry.ingredientId}`}
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
