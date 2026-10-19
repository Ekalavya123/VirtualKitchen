import { useEffect, useMemo, useRef, useState } from 'react'
import type { Recipe } from '../../api'
import { EditRecipeDetailsModal } from '../recipe-tool'
import { useStartRecipeOrder } from '../recipe-order'
import ListEnd from '../../shared/components/ListEnd'
import { draftKeys } from '../../shared/drafts/draftStore'
import { useDraft } from '../../shared/drafts/useDraft'
import { errorMessage } from '../../shared/errors'
import { useIncrementalReveal } from '../../shared/hooks/useIncrementalReveal'
import '../../styles/flow-ui.css'
import ConfirmVisibilityModal from './components/ConfirmVisibilityModal'
import CreateRecipeModal from './components/CreateRecipeModal'
import EmptyState from './components/EmptyState'
import OrderRecipeModal from './components/OrderRecipeModal'
import RecipeCard from './components/RecipeCard'
import RecipeToolbar from './components/RecipeToolbar'
import { useRecipeLibrary, type RecipeTab } from './useRecipeLibrary'

type RecipeLibraryPageProps = {
  userId: number
  /** Opens a recipe in read mode — any card, own or global. The app routes it to the Recipe Tool's Recipe Process. */
  onOpenRecipe?: (recipeId: number) => void
  /** Called right after creating a recipe. The app routes it to the Recipe Editor. */
  onCreateRecipe?: (recipeId: number, title: string) => void
}

const HIGHLIGHT_MS = 3000

/** The recipe library: the user's recipes and the public ones, with create, order, edit, share and copy. */
export default function RecipeLibraryPage({ userId, onOpenRecipe, onCreateRecipe }: RecipeLibraryPageProps) {
  const [activeTab, setActiveTab] = useState<RecipeTab>('mine')
  const library = useRecipeLibrary(userId, activeTab)
  const { startOrder, orderingRecipeId } = useStartRecipeOrder()

  // The create-recipe form keeps what was typed until the recipe is created, so closing the dialog
  // or leaving the page doesn't lose it.
  const createDraft = useDraft(draftKeys.newRecipe(), { title: '', description: '' })
  const { title, description } = createDraft.value
  const [createModalOpen, setCreateModalOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)

  const [search, setSearch] = useState('')
  const [orderModalOpen, setOrderModalOpen] = useState(false)
  const [editingRecipe, setEditingRecipe] = useState<Recipe | null>(null)
  const [visibilityRecipe, setVisibilityRecipe] = useState<Recipe | null>(null)
  const [visibilityLoading, setVisibilityLoading] = useState(false)

  const [highlightedRecipeId, setHighlightedRecipeId] = useState<number | null>(null)
  const highlightTimeoutRef = useRef<number | null>(null)
  useEffect(() => () => {
    if (highlightTimeoutRef.current !== null) window.clearTimeout(highlightTimeoutRef.current)
  }, [])

  const filteredRecipes = useMemo(() => {
    const source = activeTab === 'mine' ? library.recipes : library.globalRecipes
    const query = search.trim().toLowerCase()
    if (!query) return source
    return source.filter((recipe) =>
      recipe.name.toLowerCase().includes(query) || recipe.description?.toLowerCase().includes(query))
  }, [library.recipes, library.globalRecipes, activeTab, search])

  // Global recipes the user already added, mapped to their own copy, so a card offers "Open" instead of a duplicate.
  const ownCopyIdBySource = useMemo(() => {
    const map = new Map<number, number>()
    for (const recipe of library.recipes) {
      if (recipe.copiedFromRecipeId != null && !map.has(recipe.copiedFromRecipeId)) map.set(recipe.copiedFromRecipeId, recipe.id)
    }
    return map
  }, [library.recipes])

  // Long lists render a page at a time, revealing more as the user scrolls.
  const {
    visible: visibleRecipes,
    hasMore: hasMoreRecipes,
    sentinelRef: recipesSentinelRef,
    showMore: showMoreRecipes,
  } = useIncrementalReveal(filteredRecipes, `${activeTab}|${search.trim().toLowerCase()}`)

  const openCreateModal = () => {
    setCreateError(null)
    setCreateModalOpen(true)
  }

  const closeCreateModal = () => {
    if (creating) return
    setCreateModalOpen(false)
    setCreateError(null)
  }

  const handleCreate = async () => {
    if (!title.trim()) {
      setCreateError('Please enter a recipe name')
      return
    }
    try {
      setCreating(true)
      setCreateError(null)
      const recipeTitle = title.trim()
      const recipeId = await library.createRecipe(recipeTitle, description.trim())
      setCreateModalOpen(false)
      createDraft.clear()
      onCreateRecipe?.(recipeId, recipeTitle)
    } catch (error) {
      setCreateError(errorMessage(error, 'Unable to create recipe'))
    } finally {
      setCreating(false)
    }
  }

  const confirmToggleVisibility = async () => {
    if (!visibilityRecipe) return
    setVisibilityLoading(true)
    const done = await library.toggleVisibility(visibilityRecipe)
    setVisibilityLoading(false)
    if (done) setVisibilityRecipe(null)
  }

  const handleAddToMyRecipes = async (recipe: Recipe) => {
    const copyId = await library.addToMyRecipes(recipe)
    if (copyId == null) return
    setActiveTab('mine')
    if (highlightTimeoutRef.current !== null) window.clearTimeout(highlightTimeoutRef.current)
    setHighlightedRecipeId(copyId)
    highlightTimeoutRef.current = window.setTimeout(() => setHighlightedRecipeId(null), HIGHLIGHT_MS)
  }

  const listLoading = activeTab === 'mine' ? library.recipesLoading : library.globalRecipesLoading

  return (
    <div className="flow-editor-shell min-h-screen bg-[linear-gradient(135deg,var(--flow-bg)_0%,var(--flow-accent-soft)_100%)] px-4 py-5 sm:px-6 sm:py-7">
      <div className="mx-auto max-w-7xl">
        <header className="mb-7 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <div className="mb-1 text-[0.7rem] font-bold uppercase tracking-[0.16em] text-[var(--flow-accent)]">
              Virtual Kitchen
            </div>
            <h1 className="text-3xl font-extrabold tracking-tight text-[var(--flow-text)]">Recipes</h1>
            <p className="mt-1.5 text-sm text-[var(--flow-text-muted)]">Create, manage and build your cooking flows.</p>
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
              <button onClick={openCreateModal} className="recipe-primary-button">
                <span className="text-base leading-none">+</span>
                Create recipe
              </button>
            )}
          </div>
        </header>

        <div className="recipe-tabs mb-6">
          <button onClick={() => setActiveTab('mine')} className={`recipe-tab-button ${activeTab === 'mine' ? 'active' : ''}`}>
            My Recipes
          </button>
          <button onClick={() => setActiveTab('global')} className={`recipe-tab-button ${activeTab === 'global' ? 'active' : ''}`}>
            Global Recipes
          </button>
        </div>

        <RecipeToolbar search={search} onSearchChange={setSearch} recipeCount={filteredRecipes.length} />

        {listLoading ? (
          <div className="recipe-grid">
            {Array.from({ length: 6 }).map((_, index) => (
              <div key={index} className="recipe-card recipe-card-skeleton">
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
          <EmptyState searching={Boolean(search.trim())} tab={activeTab} onCreate={openCreateModal} />
        ) : (
          <>
            <div className="recipe-grid">
              {visibleRecipes.map((recipe) => (
                <RecipeCard
                  key={recipe.id}
                  recipe={recipe}
                  variant={activeTab}
                  highlighted={recipe.id === highlightedRecipeId}
                  onOpen={() => onOpenRecipe?.(recipe.id)}
                  onEditDetails={() => setEditingRecipe(recipe)}
                  onDelete={() => void library.deleteRecipe(recipe)}
                  onToggleVisibility={() => setVisibilityRecipe(recipe)}
                  onAddToMyRecipes={() => void handleAddToMyRecipes(recipe)}
                  ownCopyId={activeTab === 'global' ? ownCopyIdBySource.get(recipe.id) : undefined}
                  onOpenOwnCopy={() => {
                    const copyId = ownCopyIdBySource.get(recipe.id)
                    if (copyId != null) onOpenRecipe?.(copyId)
                  }}
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

      <CreateRecipeModal
        open={createModalOpen}
        title={title}
        description={description}
        loading={creating}
        error={createError}
        onTitleChange={(value) => createDraft.setValue((current) => ({ ...current, title: value }))}
        onDescriptionChange={(value) => createDraft.setValue((current) => ({ ...current, description: value }))}
        onCreate={() => void handleCreate()}
        onClose={closeCreateModal}
        draftRestored={createDraft.restored}
        onClearDraft={createDraft.clear}
      />

      <EditRecipeDetailsModal
        recipe={editingRecipe}
        onSave={async (details) => {
          if (!editingRecipe) return
          await library.saveDetails(editingRecipe, details)
          setEditingRecipe(null)
        }}
        onClose={() => setEditingRecipe(null)}
      />

      <OrderRecipeModal
        open={orderModalOpen}
        recipes={library.recipes}
        loading={library.recipesLoading}
        orderingRecipeId={orderingRecipeId}
        onOrder={(recipe) => void startOrder(recipe.id)}
        onCreateRecipe={openCreateModal}
        onClose={() => setOrderModalOpen(false)}
      />

      <ConfirmVisibilityModal
        recipe={visibilityRecipe}
        loading={visibilityLoading}
        onConfirm={() => void confirmToggleVisibility()}
        onClose={() => {
          if (!visibilityLoading) setVisibilityRecipe(null)
        }}
      />
    </div>
  )
}
