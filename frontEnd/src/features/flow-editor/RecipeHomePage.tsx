import { useEffect, useMemo, useState } from 'react'
import { RecipeApi } from '../../api'
import './styles/flow-editor.css'
import recipeIcon from '../../assets/kitchen/recipeIcon.png'

type Recipe = {
  id: number
  name: string
  description?: string
  createdAt?: string
}

type RecipeHomePageProps = {
  onCreateRecipe?: (recipeId: number, title: string) => void
  onOpenRecipies?: (recipeId: number, title: string) => void
}

type RecipeCardProps = {
  recipe: Recipe
  onOpen: () => void
  onDelete: () => void
}

function RecipeCard({ recipe, onOpen, onDelete }: RecipeCardProps) {
  return (
    <article className="recipe-card group">
      <button
        onClick={onOpen}
        className="recipe-card-content"
        aria-label={`Open ${recipe.name}`}
      >
        <div className="recipe-card-image">
          <div className="recipe-card-image-placeholder">
            <img src={recipeIcon} alt="Recipe" className="recipe-card-logo" />
          </div>
        </div>

        <div className="recipe-card-body">
          <div className="mb-1 flex items-start justify-between gap-3">
            <h3 className="line-clamp-1 text-base font-bold text-[var(--flow-text)]">
              {recipe.name}
            </h3>
          </div>

          <p className="recipe-card-description">
            {recipe.description || 'Open this recipe to edit the cooking flow.'}
          </p>

          <div className="mt-4 flex items-center justify-between">
            <span className="text-xs font-medium text-[var(--flow-text-muted)]">
              Recipe
            </span>

            {recipe.createdAt && (
              <span className="text-xs text-[var(--flow-text-muted)]">
                {formatDate(recipe.createdAt)}
              </span>
            )}
          </div>
        </div>
      </button>

      <div className="recipe-card-footer">
        <button
          onClick={onOpen}
          className="recipe-card-open-button"
        >
          Open recipe
          <span aria-hidden="true">→</span>
        </button>

        <button
          onClick={onDelete}
          title="Delete recipe"
          aria-label={`Delete ${recipe.name}`}
          className="recipe-card-delete-button"
        >
          🗑️
        </button>
      </div>
    </article>
  )
}

type RecipeToolbarProps = {
  search: string
  onSearchChange: (value: string) => void
  recipeCount: number
}

function RecipeToolbar({
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

type CreateRecipeModalProps = {
  open: boolean
  title: string
  description: string
  loading: boolean
  error: string | null
  onTitleChange: (value: string) => void
  onDescriptionChange: (value: string) => void
  onCreate: () => void
  onClose: () => void
}

function CreateRecipeModal({
  open,
  title,
  description,
  loading,
  error,
  onTitleChange,
  onDescriptionChange,
  onCreate,
  onClose,
}: CreateRecipeModalProps) {
  if (!open) {
    return null
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={e => {
        if (e.target === e.currentTarget && !loading) {
          onClose()
        }
      }}
    >
      <div
        className="recipe-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-recipe-title"
      >
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Virtual Kitchen
            </div>

            <h2
              id="create-recipe-title"
              className="text-xl font-bold text-[var(--flow-text)]"
            >
              Create a recipe
            </h2>

            <p className="mt-1 text-sm text-[var(--flow-text-muted)]">
              Start with the basics and build the cooking flow next.
            </p>
          </div>

          <button
            onClick={onClose}
            disabled={loading}
            className="recipe-modal-close"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="recipe-modal-body">
          <label className="block">
            <div className="mb-1.5 text-xs font-bold text-slate-700">
              Recipe name
            </div>

            <input
              autoFocus
              value={title}
              onChange={e => onTitleChange(e.target.value)}
              onKeyDown={e => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  onCreate()
                }
              }}
              placeholder="e.g. Spaghetti Aglio e Olio"
              className="flow-editor-input"
            />
          </label>

          <label className="mt-4 block">
            <div className="mb-1.5 text-xs font-bold text-slate-700">
              Description
              <span className="ml-1 font-normal text-slate-400">
                optional
              </span>
            </div>

            <textarea
              value={description}
              onChange={e => onDescriptionChange(e.target.value)}
              placeholder="Briefly describe the recipe..."
              rows={4}
              className="flow-editor-input resize-none"
            />
          </label>

          {error && (
            <div className="recipe-form-error">
              {error}
            </div>
          )}
        </div>

        <div className="recipe-modal-footer">
          <button
            onClick={onClose}
            disabled={loading}
            className="recipe-secondary-button"
          >
            Cancel
          </button>

          <button
            onClick={onCreate}
            disabled={loading}
            className="recipe-primary-button"
          >
            {loading ? 'Creating...' : 'Create recipe'}
            {!loading && <span aria-hidden="true">→</span>}
          </button>
        </div>
      </div>
    </div>
  )
}

function EmptyState({
  searching,
  onCreate,
}: {
  searching: boolean
  onCreate: () => void
}) {
  if (searching) {
    return (
      <div className="recipe-empty-state">
        <div className="recipe-empty-icon">🔍</div>

        <h3>No recipes found</h3>

        <p>
          Try searching with a different recipe name or description.
        </p>
      </div>
    )
  }

  return (
    <div className="recipe-empty-state">
      <div className="recipe-empty-icon">🍳</div>

      <h3>No recipes yet</h3>

      <p>
        Create your first recipe and start designing its cooking flow.
      </p>

      <button
        onClick={onCreate}
        className="recipe-primary-button mt-5"
      >
        Create your first recipe
        <span aria-hidden="true">→</span>
      </button>
    </div>
  )
}

function formatDate(value: string) {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return ''
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

export default function RecipeHomePage({
  onCreateRecipe,
  onOpenRecipies,
}: RecipeHomePageProps) {
  const handleOpenRecipe =
    onCreateRecipe || onOpenRecipies

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [recipesLoading, setRecipesLoading] = useState(false)

  const [search, setSearch] = useState('')
  const [createModalOpen, setCreateModalOpen] = useState(false)

  const loadRecipes = async () => {
    try {
      setRecipesLoading(true)

      const items = await RecipeApi.getRecipesByUserId(1)

      setRecipes(items || [])
    } catch (err) {
      console.error(err)
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to load recipes',
      )
    } finally {
      setRecipesLoading(false)
    }
  }

  useEffect(() => {
    void loadRecipes()
  }, [])

  const filteredRecipes = useMemo(() => {
    const query = search.trim().toLowerCase()

    if (!query) {
      return recipes
    }

    return recipes.filter(recipe => {
      return (
        recipe.name.toLowerCase().includes(query) ||
        recipe.description?.toLowerCase().includes(query)
      )
    })
  }, [recipes, search])

  const openCreateModal = () => {
    setTitle('')
    setDescription('')
    setError(null)
    setCreateModalOpen(true)
  }

  const closeCreateModal = () => {
    if (loading) {
      return
    }

    setCreateModalOpen(false)
    setError(null)
  }

  const handleCreate = async () => {
    if (!title.trim()) {
      setError('Please enter a recipe name')
      return
    }

    try {
      setLoading(true)
      setError(null)

      const recipeTitle = title.trim()

      const result = await RecipeApi.createRecipe({
        name: recipeTitle,
        description: description.trim(),
        createdBy: 1,
      })

      if (!result.id) {
        throw new Error('Recipe created but no id returned')
      }

      setCreateModalOpen(false)
      setTitle('')
      setDescription('')

      handleOpenRecipe?.(result.id, recipeTitle)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to create recipe',
      )
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteRecipe = async (recipeId: number) => {
    try {
      await RecipeApi.deleteRecipe(recipeId)

      setRecipes(current =>
        current.filter(recipe => recipe.id !== recipeId),
      )
    } catch (err) {
      console.error(err)

      setError(
        err instanceof Error
          ? err.message
          : 'Unable to delete recipe',
      )
    }
  }

  return (
    <div className="flow-editor-shell min-h-screen bg-[linear-gradient(135deg,#f8fafc_0%,#eef2ff_100%)] px-4 py-5 sm:px-6 sm:py-7">
      <div className="mx-auto max-w-7xl">
        {/* Header */}
        <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.16em] text-[var(--flow-accent)]">
              Virtual Kitchen
            </div>

            <h1 className="text-3xl font-extrabold tracking-tight text-[var(--flow-text)]">
              Recipes
            </h1>

            <p className="mt-1.5 text-sm text-[var(--flow-text-muted)]">
              Create, manage and build your cooking flows.
            </p>
          </div>

          <button
            onClick={openCreateModal}
            className="recipe-primary-button self-start sm:self-auto"
          >
            <span className="text-base leading-none">+</span>
            Create recipe
          </button>
        </header>

        {/* Search */}
        <RecipeToolbar
          search={search}
          onSearchChange={setSearch}
          recipeCount={filteredRecipes.length}
        />

        {/* Error */}
        {error && !createModalOpen && (
          <div className="recipe-form-error mb-5">
            {error}
          </div>
        )}

        {/* Loading */}
        {recipesLoading ? (
          <div className="recipe-grid">
            {Array.from({ length: 6 }).map((_, index) => (
              <div
                key={index}
                className="recipe-card recipe-card-skeleton"
              >
                <div className="recipe-skeleton-image" />

                <div className="p-4">
                  <div className="recipe-skeleton-line w-3/4" />
                  <div className="recipe-skeleton-line mt-3 w-full" />
                  <div className="recipe-skeleton-line mt-2 w-2/3" />
                </div>
              </div>
            ))}
          </div>
        ) : filteredRecipes.length === 0 ? (
          <EmptyState
            searching={Boolean(search.trim())}
            onCreate={openCreateModal}
          />
        ) : (
          <div className="recipe-grid">
            {filteredRecipes.map(recipe => (
              <RecipeCard
                key={recipe.id}
                recipe={recipe}
                onOpen={() =>
                  handleOpenRecipe?.(
                    recipe.id,
                    recipe.name,
                  )
                }
                onDelete={() =>
                  void handleDeleteRecipe(recipe.id)
                }
              />
            ))}
          </div>
        )}
      </div>

      {/* Create Recipe Modal */}
      <CreateRecipeModal
        open={createModalOpen}
        title={title}
        description={description}
        loading={loading}
        error={error}
        onTitleChange={setTitle}
        onDescriptionChange={setDescription}
        onCreate={() => void handleCreate()}
        onClose={closeCreateModal}
      />
    </div>
  )
}