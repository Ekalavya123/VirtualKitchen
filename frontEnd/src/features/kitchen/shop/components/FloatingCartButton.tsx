import CartBadge from './CartBadge'
import { formatMoney } from '../../../../shared/format/money'

export default function FloatingCartButton({
  visible,
  cartCount,
  cartTotal,
  onOpenCart,
}: {
  visible: boolean
  cartCount: number
  cartTotal: number
  onOpenCart: () => void
}) {
  return (
    <button
      type="button"
      className={`shop-cart-fab${visible ? ' visible' : ''}`}
      onClick={onOpenCart}
      aria-label={`Open cart with ${cartCount} items`}
      aria-hidden={!visible}
      tabIndex={visible ? 0 : -1}
    >
      <span className="shop-cart-icon" aria-hidden="true">
        🛒
      </span>
      <span className="shop-cart-fab-total">{formatMoney(cartTotal)}</span>
      <CartBadge count={cartCount} />
    </button>
  )
}
