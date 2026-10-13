type RecipeToolbarProps = {
  search: string
  onSearchChange: (value: string) => void
  recipeCount: number
}

export default function RecipeToolbar({
  search,
  onSearchChange,
  recipeCount,
}: RecipeToolbarProps) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="relative min-w-0 flex-1 sm:max-w-xl">
        <span
          className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[var(--flow-text-muted)]"
          aria-hidden="true"
        >
          🔍
        </span>

        <input
          value={search}
          onChange={e => onSearchChange(e.target.value)}
          placeholder="Search recipes..."
          className="recipe-search-input"
        />

        {search && (
          <button
            onClick={() => onSearchChange('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-[var(--flow-text-muted)] hover:text-[var(--flow-text)]"
            aria-label="Clear search"
          >
            ✕
          </button>
        )}
      </div>

      <div className="shrink-0 text-sm text-[var(--flow-text-muted)]">
        {recipeCount} {recipeCount === 1 ? 'recipe' : 'recipes'}
      </div>
    </div>
  )
}
