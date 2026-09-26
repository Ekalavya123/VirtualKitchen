import { useState } from 'react'
import '../styles/recipe-tool.css'
import type { RecipeDetail } from '../../../types/recipe'
import RecipeSummarySection from './RecipeSummarySection'

export type RecipeToolView = 'RECIPE_PROCESS' | 'INGREDIENTS' | 'NUTRITION'

const TABS: { id: RecipeToolView; label: string; icon: string }[] = [
  { id: 'RECIPE_PROCESS', label: 'Recipe Process', icon: '🧩' },
  { id: 'INGREDIENTS', label: 'Ingredients', icon: '🥕' },
  { id: 'NUTRITION', label: 'Nutritions', icon: '📊' },
]

type RecipeToolNavbarProps = {
  recipe: RecipeDetail
  activeView: RecipeToolView
  onChangeView: (view: RecipeToolView) => void
  onBack: () => void
}

/**
 * The Recipe Tool's own internal top navbar: compact recipe metadata on the
 * left (collapsing to just an icon button on narrow viewports, via Tailwind
 * responsive classes rather than a resize listener) and the three tool tabs
 * on the right. Switching tabs is local state owned by RecipeToolPage — this
 * component only reports the intent upward, it doesn't own `activeView`.
 * There is no recipe-level image in the current backend model (only
 * ingredients have one), so the compact metadata uses a plain icon rather
 * than fabricating a thumbnail.
 */
export default function RecipeToolNavbar({ recipe, activeView, onChangeView, onBack }: RecipeToolNavbarProps) {
  const [showDetails, setShowDetails] = useState(false)

  return (
    <div
      className="h-[3.75rem] flex-shrink-0 items-center gap-3 border-b border-[var(--flow-border)] bg-[var(--flow-surface)] px-4"
      style={{ position: 'relative', display: 'grid', gridTemplateColumns: '1fr auto 1fr' }}
    >
      <div className="flex min-w-0 items-center gap-2">
        <button
          onClick={onBack}
          style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid var(--flow-border)', background: 'var(--flow-surface)', cursor: 'pointer', fontSize: 12, fontWeight: 600, flexShrink: 0 }}
        >
          ← Back
        </button>

        <button
          type="button"
          onClick={() => setShowDetails((value) => !value)}
          className="flex min-w-0 items-center gap-2 rounded-lg px-2 py-1.5"
          style={{ border: '1px solid transparent', background: showDetails ? 'var(--flow-surface-muted)' : 'transparent', cursor: 'pointer' }}
          title="View full recipe details"
        >
          <span
            aria-hidden
            style={{
              width: 28, height: 28, borderRadius: 8, flexShrink: 0,
              background: 'linear-gradient(135deg, var(--flow-accent), var(--flow-accent-secondary))',
              display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14,
            }}
          >
            🍳
          </span>
          <span className="hidden min-w-0 flex-col items-start sm:flex">
            <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--flow-text)', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {recipe.name}
            </span>
            {recipe.description && (
              <span className="hidden lg:inline" style={{ fontSize: 10.5, color: 'var(--flow-text-muted)', maxWidth: 260, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {recipe.description}
              </span>
            )}
          </span>
          <span
            className="hidden sm:inline"
            style={{
              fontSize: 9, fontWeight: 700, padding: '2px 7px', borderRadius: 999, flexShrink: 0,
              background: recipe.visibility === 'PUBLIC' ? '#eff6ff' : 'var(--flow-surface-muted)',
              border: `1px solid ${recipe.visibility === 'PUBLIC' ? '#bfdbfe' : 'var(--flow-border)'}`,
              color: recipe.visibility === 'PUBLIC' ? '#1d4ed8' : 'var(--flow-text-muted)',
            }}
          >
            {recipe.visibility === 'PUBLIC' ? '🌐' : '🔒'}
          </span>
          <span style={{ fontSize: 11, color: 'var(--flow-text-subtle)', transform: showDetails ? 'rotate(180deg)' : 'none', flexShrink: 0 }} aria-hidden>
            ▾
          </span>
        </button>
      </div>

      <div className="flex flex-shrink-0 items-center gap-1 rounded-lg border border-[var(--flow-border)] bg-[var(--flow-surface-muted)] p-1">
        {TABS.map((tab) => {
          const active = tab.id === activeView
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => onChangeView(tab.id)}
              style={{
                padding: '7px 12px',
                borderRadius: 7,
                border: 'none',
                background: active ? 'var(--flow-surface)' : 'transparent',
                boxShadow: active ? '0 1px 3px rgba(15,23,42,0.12)' : 'none',
                color: active ? 'var(--flow-text)' : 'var(--flow-text-muted)',
                fontSize: 12,
                fontWeight: 700,
                cursor: 'pointer',
                whiteSpace: 'nowrap',
              }}
            >
              <span className="hidden sm:inline">{tab.icon} {tab.label}</span>
              <span className="sm:hidden" aria-label={tab.label}>{tab.icon}</span>
            </button>
          )
        })}
      </div>

      {showDetails && (
        <div
          style={{
            position: 'absolute', top: '100%', left: 12, marginTop: 6, zIndex: 30,
            width: 'min(420px, calc(100vw - 24px))', boxShadow: '0 12px 28px rgba(15,23,42,0.18)',
          }}
        >
          <RecipeSummarySection recipe={recipe} />
        </div>
      )}
    </div>
  )
}
