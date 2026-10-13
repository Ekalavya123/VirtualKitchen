export default function ShopSearch({
  search,
  onSearchChange,
}: {
  search: string
  onSearchChange: (value: string) => void
}) {
  return (
    <div className="shop-search-wrapper">
      <span className="shop-search-icon" aria-hidden="true">
        🔍
      </span>

      <input
        className="shop-search-input"
        value={search}
        onChange={event => onSearchChange(event.target.value)}
        placeholder="Search ingredients or equipment..."
        aria-label="Search shop items"
      />

      {search && (
        <button
          className="shop-search-clear"
          onClick={() => onSearchChange('')}
          aria-label="Clear search"
        >
          ✕
        </button>
      )}
    </div>
  )
}
