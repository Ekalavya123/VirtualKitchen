import { useEffect, useMemo, useRef, useState } from 'react'
import { RecipeApi, type Recipe } from '../../api'
import { useNotifications } from '../../shared/components/notifications/NotificationProvider'
import '../recipe-tool/styles/recipe-tool.css'
import recipeIcon from '../../assets/kitchen/recipeIcon.png'
import { browserStorage, clearRecovery } from '../recipe-tool/persistence/recoveryStore'
import { clearRecipeDrafts, draftKeys } from '../../shared/drafts/draftStore'
import { useDraft } from '../../shared/drafts/useDraft'
import DraftRestoredNote from '../../shared/drafts/DraftRestoredNote'
import { useStartRecipeOrder } from '../recipe-order/useStartRecipeOrder'
import { useConfirm } from '../../shared/components/confirm/confirmContext'
import { useIncrementalReveal } from '../../shared/hooks/useIncrementalReveal'
import ListEnd from '../../shared/components/ListEnd'

type RecipeTab = 'mine' | 'global'

type RecipeHomePageProps = {
  userId: number
  /** Opens a recipe — called for "Open recipe" and right after creating one. App.tsx routes it to the Process Tool. */
  onCreateRecipe?: (recipeId: number, title: string) => void
  onOpenRecipies?: (recipeId: number, title: string) => void
  /** Optional secondary "Recipe Tool" card button. No longer passed by App.tsx, since "Open recipe" itself now opens the Recipe Tool. */
  onOpenRecipeTool?: (recipeId: number) => void
}

type RecipeCardProps = {
  recipe: Recipe
  variant: RecipeTab
  highlighted?: boolean
  onOpen: () => void
  onDelete?: () => void
  onToggleVisibility?: () => void
  onAddToMyRecipes?: () => void
  onOpenRecipeTool?: () => void
}

function RecipeCard({
  recipe,
  variant,
  highlighted,
  onOpen,
  onDelete,
  onToggleVisibility,
  onAddToMyRecipes,
  onOpenRecipeTool,
}: RecipeCardProps) {
  // Falls back to the recipe icon when there is no thumbnail or it fails to load.
  const [failedThumbnail, setFailedThumbnail] = useState<string | null>(null)
  const showThumbnail = Boolean(recipe.thumbnailUrl) && recipe.thumbnailUrl !== failedThumbnail
  const isPublic = recipe.visibility === 'PUBLIC'

  // My Recipes: everything but the main action lives in the card's "⋯" menu.
  const menuItems: RecipeCardMenuItem[] = variant === 'mine'
    ? [
        ...(onOpenRecipeTool ? [{ label: '🧰 Recipe Tool', onSelect: onOpenRecipeTool }] : []),
        ...(onToggleVisibility
          ? [{ label: isPublic ? '🔒 Make private' : '🌐 Make public', onSelect: onToggleVisibility }]
          : []),
        ...(onDelete ? [{ label: '🗑️ Delete recipe', onSelect: onDelete, danger: true }] : []),
      ]
    : []

  return (
    <article className={`recipe-card group${highlighted ? ' recipe-card-highlighted' : ''}`}>
      {/* The whole card opens the recipe (Global Recipes: its preview). */}
      <button
        onClick={onOpen}
        className="recipe-card-content"
        aria-label={`Open ${recipe.name}`}
      >
        <div className="recipe-card-image">
          {showThumbnail ? (
            <img
              src={recipe.thumbnailUrl!}
              alt={recipe.name}
              className="recipe-card-thumbnail"
              loading="lazy"
              onError={() => setFailedThumbnail(recipe.thumbnailUrl ?? null)}
            />
          ) : (
            <div className="recipe-card-image-placeholder">
              <img src={recipeIcon} alt="Recipe" className="recipe-card-logo" />
            </div>
          )}

          {variant === 'mine' && (
            <span className="recipe-card-badge">
              {isPublic ? '🌐 Public' : '🔒 Private'}
            </span>
          )}
        </div>

        <div className="recipe-card-body">
          <h3 className="line-clamp-1 text-base font-bold text-[var(--flow-text)]">
            {recipe.name}
          </h3>

          {recipe.description?.trim() && (
            <p className="recipe-card-description">
              {recipe.description}
            </p>
          )}
        </div>
      </button>

      <div className="recipe-card-footer">
        <span className="recipe-card-meta">
          {recipe.createdAt ? formatDate(recipe.createdAt) : ''}
        </span>

        {variant === 'global' ? (
          <button
            onClick={onAddToMyRecipes}
            className="recipe-card-order-button"
          >
            + Add to My Recipes
          </button>
        ) : (
          menuItems.length > 0 && (
            <RecipeCardMenu recipeName={recipe.name} items={menuItems} />
          )
        )}
      </div>
    </article>
  )
}

type RecipeCardMenuItem = {
  label: string
  onSelect: () => void
  danger?: boolean
}

/** A card's "⋯" menu: closes on choosing an item, clicking elsewhere, or Escape. */
function RecipeCardMenu({ recipeName, items }: { recipeName: string; items: RecipeCardMenuItem[] }) {
  const [open, setOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div ref={menuRef} className={`recipe-card-menu${open ? ' open' : ''}`}>
      <button
        type="button"
        className="recipe-card-menu-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`More actions for ${recipeName}`}
        onClick={() => setOpen(current => !current)}
      >
        ⋯
      </button>

      {open && (
        <div className="recipe-card-menu-popover" role="menu">
          {items.map(item => (
            <button
              key={item.label}
              type="button"
              role="menuitem"
              className={`recipe-card-menu-item${item.danger ? ' danger' : ''}`}
              onClick={() => {
                setOpen(false)
                item.onSelect()
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

type OrderRecipeModalProps = {
  open: boolean
  recipes: Recipe[]
  loading: boolean
  /** The recipe whose order is being created. */
  orderingRecipeId: number | null
  onOrder: (recipe: Recipe) => void
  onCreateRecipe: () => void
  onClose: () => void
}

/** "Order recipe": choose one of your recipes, then continue to its order's confirm page. */
function OrderRecipeModal({
  open,
  recipes,
  loading,
  orderingRecipeId,
  onOrder,
  onCreateRecipe,
  onClose,
}: OrderRecipeModalProps) {
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && orderingRecipeId == null) onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, orderingRecipeId, onClose])

  if (!open) return null

  const busy = orderingRecipeId != null
  const search = query.trim().toLowerCase()
  const matches = search
    ? recipes.filter(recipe =>
        recipe.name.toLowerCase().includes(search) || recipe.description?.toLowerCase().includes(search))
    : recipes
  const selected = recipes.find(recipe => recipe.id === selectedId) ?? null

  const close = () => {
    if (busy) return
    setQuery('')
    setSelectedId(null)
    onClose()
  }

  return (
    <div
      className="recipe-modal-backdrop"
      onMouseDown={event => {
        if (event.target === event.currentTarget) close()
      }}
    >
      <div className="recipe-modal order-recipe-modal" role="dialog" aria-modal="true" aria-labelledby="order-recipe-title">
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              Order recipe
            </div>
            <h2 id="order-recipe-title" className="text-xl font-bold text-[var(--flow-text)]">
              Which recipe would you like prepared?
            </h2>
            <p className="mt-1 text-sm text-[var(--flow-text-muted)]">
              Pick one of your recipes. Next you'll confirm servings and the delivery address.
            </p>
          </div>

          <button onClick={close} disabled={busy} className="recipe-modal-close" aria-label="Close">
            ✕
          </button>
        </div>

        <div className="recipe-modal-body order-recipe-body">
          {loading ? (
            <p className="text-sm text-[var(--flow-text-muted)]">Loading your recipes…</p>
          ) : recipes.length === 0 ? (
            <div className="order-recipe-empty">
              <p>You don't have any recipes to order yet.</p>
              <button type="button" className="recipe-primary-button" onClick={() => { close(); onCreateRecipe() }}>
                + Create recipe
              </button>
            </div>
          ) : (
            <>
              <input
                value={query}
                onChange={event => setQuery(event.target.value)}
                placeholder="Search your recipes..."
                className="recipe-search-input order-recipe-search"
                aria-label="Search your recipes"
                autoFocus
              />

              <div className="order-recipe-list" role="radiogroup" aria-label="Your recipes">
                {matches.length === 0 ? (
                  <p className="text-sm text-[var(--flow-text-muted)]">No recipes match "{query}".</p>
                ) : (
                  matches.map(recipe => (
                    <label
                      key={recipe.id}
                      className={`order-recipe-option${recipe.id === selectedId ? ' selected' : ''}`}
                    >
                      <input
                        type="radio"
                        name="order-recipe"
                        checked={recipe.id === selectedId}
                        onChange={() => setSelectedId(recipe.id)}
                        disabled={busy}
                      />
                      <span className="order-recipe-thumb" aria-hidden="true">
                        {recipe.thumbnailUrl ? (
                          <img src={recipe.thumbnailUrl} alt="" loading="lazy" />
                        ) : (
                          <img src={recipeIcon} alt="" className="order-recipe-thumb-icon" />
                        )}
                      </span>
                      <span className="order-recipe-text">
                        <span className="order-recipe-name">{recipe.name}</span>
                        {recipe.description?.trim() && (
                          <span className="order-recipe-description">{recipe.description}</span>
                        )}
                      </span>
                    </label>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        {recipes.length > 0 && (
          <div className="recipe-modal-footer">
            <button onClick={close} disabled={busy} className="recipe-secondary-button">
              Cancel
            </button>
            <button
              onClick={() => selected && onOrder(selected)}
              disabled={!selected || busy}
              className="recipe-primary-button"
            >
              {busy ? 'Starting order…' : 'Continue to order →'}
            </button>
          </div>
        )}
      </div>
    </div>
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
  /** The fields were refilled from an unfinished earlier attempt; `onClearDraft` starts over. */
  draftRestored: boolean
  onClearDraft: () => void
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
  draftRestored,
  onClearDraft,
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
          {draftRestored && (title.trim() || description.trim()) && (
            <div className="mb-3"><DraftRestoredNote onDiscard={onClearDraft} /></div>
          )}
          <label className="block">
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">
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
            <div className="mb-1.5 text-xs font-bold text-[var(--flow-text)]">
              Description
              <span className="ml-1 font-normal text-[var(--flow-text-subtle)]">
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

type ConfirmVisibilityModalProps = {
  recipe: Recipe | null
  loading: boolean
  onConfirm: () => void
  onClose: () => void
}

function ConfirmVisibilityModal({
  recipe,
  loading,
  onConfirm,
  onClose,
}: ConfirmVisibilityModalProps) {
  if (!recipe) {
    return null
  }

  const makingPublic = recipe.visibility !== 'PUBLIC'

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
        aria-labelledby="confirm-visibility-title"
      >
        <div className="recipe-modal-header">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.14em] text-[var(--flow-accent)]">
              {makingPublic ? 'Make public' : 'Make private'}
            </div>

            <h2
              id="confirm-visibility-title"
              className="text-xl font-bold text-[var(--flow-text)]"
            >
              {makingPublic ? 'Make this recipe public?' : 'Make this recipe private?'}
            </h2>
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
          <p className="text-sm text-[var(--flow-text-muted)]">
            {makingPublic
              ? 'Anyone browsing Global Recipes will be able to view this recipe and add a copy of it to their own kitchen. You can make it private again at any time.'
              : 'This recipe will be removed from Global Recipes and other users will no longer be able to find or add it. Copies already added to other kitchens will not be affected.'}
          </p>
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
            onClick={onConfirm}
            disabled={loading}
            className="recipe-primary-button"
          >
            {loading ? 'Updating...' : `Confirm`}
            {!loading && <span aria-hidden="true">→</span>}
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
  onOpenRecipeTool,
}: RecipeHomePageProps) {
  const handleOpenRecipe =
    onCreateRecipe || onOpenRecipies

  const { notifySuccess, notifyError } = useNotifications()
  const confirm = useConfirm()
  const { startOrder, orderingRecipeId } = useStartRecipeOrder()

  const [activeTab, setActiveTab] = useState<RecipeTab>('mine')

  // The create-recipe form keeps what was typed until the recipe is created, so closing the dialog
  // or leaving the page doesn't lose it.
  const createDraft = useDraft(draftKeys.newRecipe(), { title: '', description: '' })
  const { title, description } = createDraft.value
  const setTitle = (value: string) => createDraft.setValue(current => ({ ...current, title: value }))
  const setDescription = (value: string) => createDraft.setValue(current => ({ ...current, description: value }))

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [recipes, setRecipes] = useState<Recipe[]>([])
  const [recipesLoading, setRecipesLoading] = useState(false)

  const [globalRecipes, setGlobalRecipes] = useState<Recipe[]>([])
  const [globalRecipesLoading, setGlobalRecipesLoading] = useState(false)

  const [search, setSearch] = useState('')
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [viewRecipe, setViewRecipe] = useState<Recipe | null>(null)
  const [orderModalOpen, setOrderModalOpen] = useState(false)

  const [visibilityRecipe, setVisibilityRecipe] = useState<Recipe | null>(null)
  const [visibilityLoading, setVisibilityLoading] = useState(false)

  const [highlightedRecipeId, setHighlightedRecipeId] = useState<number | null>(null)
  const highlightTimeoutRef = useRef<number | null>(null)

  const loadRecipes = async () => {
    try {
      setRecipesLoading(true)

      const items = await RecipeApi.getRecipesByUserId(userId)

      setRecipes(items || [])
    } catch (err) {
      console.error(err)
      notifyError(
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
      notifyError(
        err instanceof Error
          ? err.message
          : 'Unable to load global recipes',
      )
    } finally {
      setGlobalRecipesLoading(false)
    }
  }

  useEffect(() => {
    return () => {
      if (highlightTimeoutRef.current !== null) {
        window.clearTimeout(highlightTimeoutRef.current)
      }
    }
  }, [])

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

  // Long lists render a page at a time, revealing more as the user scrolls.
  const {
    visible: visibleRecipes,
    hasMore: hasMoreRecipes,
    sentinelRef: recipesSentinelRef,
    showMore: showMoreRecipes,
  } = useIncrementalReveal(filteredRecipes, `${activeTab}|${search.trim().toLowerCase()}`)

  const openCreateModal = () => {
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
      createDraft.clear()

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

  const handleDeleteRecipe = async (recipe: Recipe) => {
    const confirmed = await confirm({
      eyebrow: 'Delete recipe',
      title: `Delete "${recipe.name}"?`,
      message:
        'The recipe, its processes and step images will be permanently removed from your kitchen. Orders already placed keep their own copy. This cannot be undone.',
      confirmLabel: 'Delete recipe',
    })
    if (!confirmed) return
    const recipeId = recipe.id
    try {
      await RecipeApi.deleteRecipe(recipeId, userId)
      // Unsaved Recipe Tool changes left on this device for a deleted recipe can never be recovered.
      clearRecovery(browserStorage(), recipeId)
      clearRecipeDrafts(recipeId)

      setRecipes(current =>
        current.filter(recipe => recipe.id !== recipeId),
      )
      notifySuccess('Recipe deleted.')
    } catch (err) {
      console.error(err)

      notifyError(
        err instanceof Error
          ? err.message
          : 'Unable to delete recipe',
      )
    }
  }

  const requestToggleVisibility = (recipe: Recipe) => {
    setVisibilityRecipe(recipe)
  }

  const closeVisibilityModal = () => {
    if (visibilityLoading) {
      return
    }

    setVisibilityRecipe(null)
  }

  const confirmToggleVisibility = async () => {
    if (!visibilityRecipe) {
      return
    }

    const recipe = visibilityRecipe
    const nextVisibility = recipe.visibility === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC'

    try {
      setVisibilityLoading(true)

      const updated = await RecipeApi.updateVisibility(recipe.id, userId, nextVisibility)

      setRecipes(current =>
        // The visibility endpoint doesn't resolve thumbnails, so keep the one already shown.
        current.map(item => (item.id === recipe.id ? { ...updated, thumbnailUrl: item.thumbnailUrl } : item)),
      )
      setVisibilityRecipe(null)
      notifySuccess(
        nextVisibility === 'PUBLIC'
          ? 'Recipe is now public.'
          : 'Recipe is now private.',
      )
    } catch (err) {
      console.error(err)

      notifyError(
        err instanceof Error
          ? err.message
          : 'Unable to update recipe visibility',
      )
    } finally {
      setVisibilityLoading(false)
    }
  }

  const handleAddToMyRecipes = async (recipe: Recipe) => {
    try {
      const copy = await RecipeApi.copyRecipe(recipe.id, userId)
      await loadRecipes()
      setActiveTab('mine')

      notifySuccess('Recipe added to your kitchen.')

      if (highlightTimeoutRef.current !== null) {
        window.clearTimeout(highlightTimeoutRef.current)
      }

      setHighlightedRecipeId(copy.id)
      highlightTimeoutRef.current = window.setTimeout(() => {
        setHighlightedRecipeId(null)
      }, 3000)
    } catch (err) {
      console.error(err)

      notifyError(
        err instanceof Error
          ? err.message
          : 'Unable to add recipe to My Recipes',
      )
    }
  }

  return (
    <div className="flow-editor-shell min-h-screen bg-[linear-gradient(135deg,var(--flow-bg)_0%,var(--flow-accent-soft)_100%)] px-4 py-5 sm:px-6 sm:py-7">
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

          <div className="flex flex-wrap items-center gap-2 self-start sm:self-auto">
            <button
              onClick={() => setOrderModalOpen(true)}
              className="recipe-secondary-button recipe-order-cta"
              title="Have one of your recipes prepared and delivered (simulated)"
            >
              🛒 Order recipe
            </button>

            {activeTab === 'mine' && (
              <button
                onClick={openCreateModal}
                className="recipe-primary-button"
              >
                <span className="text-base leading-none">+</span>
                Create recipe
              </button>
            )}
          </div>
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
          <>
          <div className="recipe-grid">
            {visibleRecipes.map(recipe => (
              <RecipeCard
                key={recipe.id}
                recipe={recipe}
                variant={activeTab}
                highlighted={recipe.id === highlightedRecipeId}
                onOpen={() =>
                  activeTab === 'mine'
                    ? handleOpenRecipe?.(recipe.id, recipe.name)
                    : setViewRecipe(recipe)
                }
                onDelete={() =>
                  void handleDeleteRecipe(recipe)
                }
                onToggleVisibility={() =>
                  requestToggleVisibility(recipe)
                }
                onAddToMyRecipes={() =>
                  void handleAddToMyRecipes(recipe)
                }
                onOpenRecipeTool={
                  onOpenRecipeTool
                    ? () => onOpenRecipeTool(recipe.id)
                    : undefined
                }
              />
            ))}
          </div>
          <ListEnd
            hasMore={hasMoreRecipes}
            shown={visibleRecipes.length}
            total={filteredRecipes.length}
            noun="recipes"
            sentinelRef={recipesSentinelRef}
            onShowMore={showMoreRecipes}
          />
          </>
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
        draftRestored={createDraft.restored}
        onClearDraft={createDraft.clear}
      />

      {/* Order Recipe: pick one of your recipes */}
      <OrderRecipeModal
        open={orderModalOpen}
        recipes={recipes}
        loading={recipesLoading}
        orderingRecipeId={orderingRecipeId}
        onOrder={recipe => void startOrder(recipe.id)}
        onCreateRecipe={openCreateModal}
        onClose={() => setOrderModalOpen(false)}
      />

      {/* View Recipe Modal (Global Recipes) */}
      <ViewRecipeModal
        recipe={viewRecipe}
        onClose={() => setViewRecipe(null)}
      />

      {/* Public/Private Confirmation Modal */}
      <ConfirmVisibilityModal
        recipe={visibilityRecipe}
        loading={visibilityLoading}
        onConfirm={() => void confirmToggleVisibility()}
        onClose={closeVisibilityModal}
      />
    </div>
  )
}