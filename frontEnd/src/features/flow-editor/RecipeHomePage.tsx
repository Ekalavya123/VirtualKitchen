import { useEffect, useMemo, useState } from 'react'
import { RecipeApi, type Recipe } from '../../api'
import './styles/flow-editor.css'
import recipeIcon from '../../assets/kitchen/recipeIcon.png'

type RecipeTab = 'mine' | 'global'

type RecipeHomePageProps = {
  userId: number
  onCreateRecipe?: (recipeId: number, title: string) => void
  onOpenRecipies?: (recipeId: number, title: string) => void
}

type RecipeCardProps = {
  recipe: Recipe
  variant: RecipeTab
  onOpen: () => void
  onDelete?: () => void
  onToggleVisibility?: () => void
  onAddToMyRecipes?: () => void
}

function RecipeCard({
  recipe,
  variant,
  onOpen,
  onDelete,
  onToggleVisibility,
  onAddToMyRecipes,
}: RecipeCardProps) {
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
          {variant === 'mine' ? 'Open recipe' : 'View recipe'}
          <span aria-hidden="true">→</span>
        </button>

        {variant === 'mine' ? (
          <div className="flex items-center gap-2">
            <button
              onClick={onToggleVisibility}
              title={recipe.visibility === 'PUBLIC' ? 'Make private' : 'Make public'}
              className="recipe-card-visibility-button"
            >
              {recipe.visibility === 'PUBLIC' ? '🌐 Public' : '🔒 Private'}
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
        ) : (
          <button
            onClick={onAddToMyRecipes}
            className="recipe-card-open-button"
          >
            + Add to My Recipes
          </button>
        )}
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
  tab,
  onCreate,
}: {
  searching: boolean
  tab: RecipeTab
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

  if (tab === 'global') {
    return (
      <div className="recipe-empty-state">
        <div className="recipe-empty-icon">🌐</div>

        <h3>No public recipes yet</h3>

        <p>
          When other users publish recipes, they will show up here.
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

function ViewRecipeModal({
  recipe,
  onClose,
}: {
  recipe: Recipe | null
  onClose: () => void
}) {
  if (!recipe) {
    return null
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={e => {
        if (e.target === e.currentTarget) {
          onClose()
        }
      }}
    >
      <div
        className="recipe-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="view-recipe-title"
      >
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Global Recipe
            </div>

            <h2
              id="view-recipe-title"
              className="text-xl font-bold text-[var(--flow-text)]"
            >
              {recipe.name}
            </h2>
          </div>

          <button
            onClick={onClose}
            className="recipe-modal-close"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="recipe-modal-body">
          <p className="text-sm text-[var(--flow-text-muted)]">
            {recipe.description || 'No description provided for this recipe.'}
          </p>
        </div>

        <div className="recipe-modal-footer">
          <button
            onClick={onClose}
            className="recipe-secondary-button"
          >
            Close
          </button>
        </div>
      </div>
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
  userId,
  onCreateRecipe,
  onOpenRecipies,
}: RecipeHomePageProps) {
  const handleOpenRecipe =
    onCreateRecipe || onOpenRecipies

  const [activeTab, setActiveTab] = useState<RecipeTab>('mine')

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [recipesLoading, setRecipesLoading] = useState(false)

  const [globalRecipes, setGlobalRecipes] = useState<Recipe[]>([])
  const [globalRecipesLoading, setGlobalRecipesLoading] = useState(false)

  const [search, setSearch] = useState('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [viewRecipe, setViewRecipe] = useState<Recipe | null>(null)

  const loadRecipes = async () => {
    try {
      setRecipesLoading(true)

      const items = await RecipeApi.getRecipesByUserId(userId)

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

  const loadGlobalRecipes = async () => {
    try {
      setGlobalRecipesLoading(true)

      const items = await RecipeApi.getGlobalRecipes(userId)

      setGlobalRecipes(items || [])
    } catch (err) {
      console.error(err)
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to load global recipes',
      )
    } finally {
      setGlobalRecipesLoading(false)
    }
  }

  useEffect(() => {
    void loadRecipes()
  }, [userId])

  useEffect(() => {
    if (activeTab === 'global') {
      void loadGlobalRecipes()
    }
  }, [activeTab, userId])

  const filteredRecipes = useMemo(() => {
    const source = activeTab === 'mine' ? recipes : globalRecipes
    const query = search.trim().toLowerCase()

    if (!query) {
      return source
    }

    return source.filter(recipe => {
      return (
        recipe.name.toLowerCase().includes(query) ||
        recipe.description?.toLowerCase().includes(query)
      )
    })
  }, [recipes, globalRecipes, activeTab, search])

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
        createdBy: userId,
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
      await RecipeApi.deleteRecipe(recipeId, userId)

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

  const handleToggleVisibility = async (recipe: Recipe) => {
    const nextVisibility = recipe.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'

    try {
      const updated = await RecipeApi.updateVisibility(recipe.id, userId, nextVisibility)

      setRecipes(current =>
        current.map(item => (item.id === recipe.id ? updated : item)),
      )
    } catch (err) {
      console.error(err)

      setError(
        err instanceof Error
          ? err.message
          : 'Unable to update recipe visibility',
      )
    }
  }

  const handleAddToMyRecipes = async (recipe: Recipe) => {
    try {
      await RecipeApi.copyRecipe(recipe.id, userId)
      await loadRecipes()
      setActiveTab('mine')
    } catch (err) {
      console.error(err)

      setError(
        err instanceof Error
          ? err.message
          : 'Unable to add recipe to My Recipes',
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

          {activeTab === 'mine' && (
            <button
              onClick={openCreateModal}
              className="recipe-primary-button self-start sm:self-auto"
            >
              <span className="text-base leading-none">+</span>
              Create recipe
            </button>
          )}
        </header>

        {/* Tabs */}
        <div className="recipe-tabs mb-6">
          <button
            onClick={() => setActiveTab('mine')}
            className={`recipe-tab-button ${activeTab === 'mine' ? 'active' : ''}`}
          >
            My Recipes
          </button>

          <button
            onClick={() => setActiveTab('global')}
            className={`recipe-tab-button ${activeTab === 'global' ? 'active' : ''}`}
          >
            Global Recipes
          </button>
        </div>

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
        {(activeTab === 'mine' ? recipesLoading : globalRecipesLoading) ? (
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
            tab={activeTab}
            onCreate={openCreateModal}
          />
        ) : (
          <div className="recipe-grid">
            {filteredRecipes.map(recipe => (
              <RecipeCard
                key={recipe.id}
                recipe={recipe}
                variant={activeTab}
                onOpen={() =>
                  activeTab === 'mine'
                    ? handleOpenRecipe?.(recipe.id, recipe.name)
                    : setViewRecipe(recipe)
                }
                onDelete={() =>
                  void handleDeleteRecipe(recipe.id)
                }
                onToggleVisibility={() =>
                  void handleToggleVisibility(recipe)
                }
                onAddToMyRecipes={() =>
                  void handleAddToMyRecipes(recipe)
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

      {/* View Recipe Modal (Global Recipes) */}
      <ViewRecipeModal
        recipe={viewRecipe}
        onClose={() => setViewRecipe(null)}
      />
    </div>
  )
}