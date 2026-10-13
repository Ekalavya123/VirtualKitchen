import type { Ref } from 'react'
import CartBadge from './CartBadge'

export default function ShopHeader({
  cartCount,
  onOpenCart,
  cartButtonRef,
}: {
  cartCount: number
  onOpenCart: () => void
  cartButtonRef: Ref<HTMLButtonElement>
}) {
  return (
    <header className="shop-header">
      <div>
        <div className="shop-eyebrow">Virtual Kitchen</div>

        <h1>Shop</h1>

        <p className="shop-subtitle">
          Find ingredients and equipment for your kitchen.
        </p>
      </div>

      <button
        ref={cartButtonRef}
        className="shop-cart-button"
        onClick={onOpenCart}
        aria-label={`Open cart with ${cartCount} items`}
      >
        <span className="shop-cart-icon" aria-hidden="true">
          🛒
        </span>

        <span className="shop-cart-label">Cart</span>

        <CartBadge count={cartCount} />
      </button>
    </header>
  )
}

/** The cart's item count; re-keyed on every change so its "bump" animation replays. */
