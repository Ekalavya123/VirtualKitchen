export default function ShopFilters({
  filter,
  onFilterChange,
}: {
  filter: 'all' | 'ingredients' | 'equipment'
  onFilterChange: (
    value: 'all' | 'ingredients' | 'equipment',
  ) => void
}) {
  return (
    <div className="shop-filters">
      <button
        className={`shop-filter-btn ${
          filter === 'all' ? 'active' : ''
        }`}
        onClick={() => onFilterChange('all')}
      >
        All Items
      </button>

      <button
        className={`shop-filter-btn ${
          filter === 'ingredients' ? 'active' : ''
        }`}
        onClick={() => onFilterChange('ingredients')}
      >
        🥘 Ingredients
      </button>

      <button
        className={`shop-filter-btn ${
          filter === 'equipment' ? 'active' : ''
        }`}
        onClick={() => onFilterChange('equipment')}
      >
        ⚙️ Equipment
      </button>
    </div>
  )
}
