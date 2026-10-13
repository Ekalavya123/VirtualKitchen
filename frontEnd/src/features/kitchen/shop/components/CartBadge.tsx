
export default function CartBadge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span key={count} className="shop-cart-badge">
      {count > 99 ? '99+' : count}
    </span>
  )
}

/**
 * A floating cart button that takes over when the header's cart button scrolls out of view, so
 * the cart stays one click away (and visibly fills up) while browsing a long catalog.
 */
