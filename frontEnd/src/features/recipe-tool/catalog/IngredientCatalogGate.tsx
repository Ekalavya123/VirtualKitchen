import { useEffect, useState, type ReactNode } from 'react'
import '../styles/recipe-tool.css'
import { isIngredientCatalogLoaded, loadIngredientCatalog, useIngredientCatalog } from './ingredientCatalog'

type IngredientCatalogGateProps = {
  children: ReactNode
}

/**
 * Renders its children only once the ingredient catalog (from the database — see
 * ingredientCatalog.ts) is loaded, since the step editor, the Recipe Process and recipe orders all
 * look ingredients up synchronously during render. Loads once per page session; later visits render
 * straight away.
 */
export default function IngredientCatalogGate({ children }: IngredientCatalogGateProps) {
  // Subscribing re-renders this gate the moment the catalog arrives (from this load or another one).
  useIngredientCatalog()
  const loaded = isIngredientCatalogLoaded()
  const [error, setError] = useState<string | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    if (isIngredientCatalogLoaded()) return
    let cancelled = false
    loadIngredientCatalog().catch((loadError: unknown) => {
      if (!cancelled) setError(loadError instanceof Error ? loadError.message : 'Unable to load the ingredient catalog')
    })
    return () => {
      cancelled = true
    }
  }, [attempt])

  if (loaded) return <>{children}</>

  if (error) {
    return (
      <div className="flex h-full min-h-[240px] w-full flex-col items-center justify-center gap-3 px-4 text-center" role="alert">
        <div style={{ fontSize: 28 }} aria-hidden>🥕</div>
        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--flow-text)' }}>The ingredient catalog couldn't be loaded</div>
        <div style={{ fontSize: 13, color: 'var(--flow-text-muted)', maxWidth: 420 }}>{error}</div>
        <button
          type="button"
          onClick={() => {
            setError(null)
            setAttempt((value) => value + 1)
          }}
          style={{
            padding: '8px 16px', borderRadius: 8, border: 0, cursor: 'pointer', fontSize: 13, fontWeight: 700, color: 'white',
            background: 'linear-gradient(135deg, var(--flow-accent), var(--flow-accent-secondary))',
          }}
        >
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="flex h-full min-h-[240px] w-full items-center justify-center" style={{ color: 'var(--flow-text-muted)', fontSize: 14 }} role="status">
      Loading ingredients…
    </div>
  )
}
