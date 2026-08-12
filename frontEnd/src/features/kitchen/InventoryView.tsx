import { useEffect, useMemo, useState, useCallback } from 'react'
import { InventoryApi, type InventoryItem } from '../../api'
import './InventoryView.css'

interface InventoryViewProps {
  kitchenId: number
  filter: 'ingredients' | 'equipment'
  onFilterChange: (filter: 'ingredients' | 'equipment') => void
}

function InventoryHeader() {
  return (
    <div className="inventory-title">
      <div className="inventory-eyebrow">
        Virtual Kitchen
      </div>

      <h1>Kitchen Inventory</h1>

      <p>
        View the ingredients and equipment currently
        available in your kitchen.
      </p>
    </div>
  )
}

function InventorySearch({
  search,
  onSearchChange,
}: {
  search: string
  onSearchChange: (value: string) => void
}) {
  return (
    <div className="inventory-search-wrapper">
      <span
        className="inventory-search-icon"
        aria-hidden="true"
      >
        🔍
      </span>

      <input
        className="inventory-search-input"
        value={search}
        onChange={event =>
          onSearchChange(event.target.value)
        }
        placeholder="Search ingredients or equipment..."
        aria-label="Search inventory"
      />

      {search && (
        <button
          className="inventory-search-clear"
          onClick={() => onSearchChange('')}
          aria-label="Clear search"
        >
          ✕
        </button>
      )}
    </div>
  )
}

function InventoryFilters({
  filter,
  onFilterChange,
}: {
  filter: 'ingredients' | 'equipment'
  onFilterChange: (
    filter: 'ingredients' | 'equipment',
  ) => void
}) {
  return (
    <div className="inventory-filters">
      <button
        className={`inventory-filter-btn ${
          filter === 'ingredients' ? 'active' : ''
        }`}
        onClick={() =>
          onFilterChange('ingredients')
        }
      >
        <span>🥘</span>
        Ingredients
      </button>

      <button
        className={`inventory-filter-btn ${
          filter === 'equipment' ? 'active' : ''
        }`}
        onClick={() =>
          onFilterChange('equipment')
        }
      >
        <span>⚙️</span>
        Equipment
      </button>
    </div>
  )
}

function InventoryCard({
  item,
}: {
  item: InventoryItem
}) {
  const isIngredient =
    item.itemType === 'INGREDIENT'

  const quantity =
    item.quantity !== undefined
      ? item.quantity
      : 0

  return (
    <article className="inventory-card">
      <div className="inventory-card-image">
        <span aria-hidden="true">
          {isIngredient ? '🥘' : '⚙️'}
        </span>
      </div>

      <div className="inventory-card-body">
        <div className="inventory-card-type">
          {isIngredient
            ? 'Ingredient'
            : 'Equipment'}
        </div>

        <h3 className="inventory-card-title">
          {item.itemName || 'Unnamed item'}
        </h3>

        <div className="inventory-quantity-box">
          <span className="inventory-quantity-label">
            Available quantity
          </span>

          <span className="inventory-quantity-value">
            {quantity}
            {item.unit && (
              <small>{item.unit}</small>
            )}
          </span>
        </div>

        <div className="inventory-card-footer">
          <span>Last updated</span>

          <strong>
            {formatDate(item.lastUpdated)}
          </strong>
        </div>
      </div>
    </article>
  )
}

function formatDate(value?: string) {
  if (!value) {
    return 'N/A'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'N/A'
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  }).format(date)
}

function InventoryEmptyState({
  searching,
  filter,
}: {
  searching: boolean
  filter: 'ingredients' | 'equipment'
}) {
  if (searching) {
    return (
      <div className="inventory-empty-state">
        <div className="inventory-empty-icon">
          🔍
        </div>

        <h3>No matching items</h3>

        <p>
          Try searching with a different ingredient
          or equipment name.
        </p>
      </div>
    )
  }

  return (
    <div className="inventory-empty-state">
      <div className="inventory-empty-icon">
        {filter === 'ingredients' ? '🥘' : '⚙️'}
      </div>

      <h3>
        No {filter} in your kitchen
      </h3>

      <p>
        Visit the shop to add{' '}
        {filter === 'ingredients'
          ? 'ingredients'
          : 'equipment'}{' '}
        to your inventory.
      </p>
    </div>
  )
}

function InventoryLoading() {
  return (
    <div className="inventory-grid">
      {Array.from({ length: 6 }).map((_, index) => (
        <div
          key={index}
          className="inventory-card inventory-card-skeleton"
        >
          <div className="inventory-skeleton-image" />

          <div className="inventory-skeleton-body">
            <div className="inventory-skeleton-line short" />
            <div className="inventory-skeleton-line title" />
            <div className="inventory-skeleton-box" />
            <div className="inventory-skeleton-line footer" />
          </div>
        </div>
      ))}
    </div>
  )
}

export default function InventoryView({
  kitchenId,
  filter,
  onFilterChange,
}: InventoryViewProps) {
  const [items, setItems] = useState<InventoryItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')

  const fetchItems = useCallback(async () => {
    try {
      setLoading(true)
      setError('')

      const fetchedItems =
        await InventoryApi.getInventoryByKitchenId(
          kitchenId,
        )

      if (
        fetchedItems &&
        Array.isArray(fetchedItems)
      ) {
        setItems(fetchedItems)
      } else {
        setError(
          'Unexpected response format from server',
        )
      }
    } catch (err) {
      console.error(
        'Error fetching inventory:',
        err,
      )

      setError(
        `Failed to load inventory: ${
          err instanceof Error
            ? err.message
            : 'Unknown error'
        }`,
      )
    } finally {
      setLoading(false)
    }
  }, [kitchenId])

  useEffect(() => {
    void fetchItems()
  }, [fetchItems])

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase()

    return items.filter(item => {
      const matchesFilter =
        filter === 'ingredients'
          ? item.itemType === 'INGREDIENT'
          : item.itemType === 'EQUIPMENT'

      if (!matchesFilter) {
        return false
      }

      if (!query) {
        return true
      }

      return (
        item.itemName
          ?.toLowerCase()
          .includes(query) ?? false
      )
    })
  }, [items, filter, search])

  return (
    <div className="inventory-view">
      <div className="inventory-header">
        <InventoryHeader />
      </div>

      <InventorySearch
        search={search}
        onSearchChange={setSearch}
      />

      <div className="inventory-toolbar">
        <InventoryFilters
          filter={filter}
          onFilterChange={onFilterChange}
        />

        {!loading && !error && (
          <div className="inventory-results-count">
            {filteredItems.length}{' '}
            {filteredItems.length === 1
              ? 'item'
              : 'items'}
          </div>
        )}
      </div>

      {loading && items.length === 0 ? (
        <InventoryLoading />
      ) : error ? (
        <div className="inventory-error-state">
          <strong>
            Unable to load inventory
          </strong>

          <span>{error}</span>

          <button
            onClick={() => void fetchItems()}
            className="inventory-retry-button"
          >
            Try again
          </button>
        </div>
      ) : filteredItems.length === 0 ? (
        <InventoryEmptyState
          searching={Boolean(search.trim())}
          filter={filter}
        />
      ) : (
        <div className="inventory-grid">
          {filteredItems.map(item => (
            <InventoryCard
              key={`${item.itemType}-${item.id}`}
              item={item}
            />
          ))}
        </div>
      )}
    </div>
  )
}