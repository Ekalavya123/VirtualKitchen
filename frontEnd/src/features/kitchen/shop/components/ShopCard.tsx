import { formatMoney } from '../../../../shared/format/money'
import CatalogItemImage from '../../components/CatalogItemImage'
import type { ShopCatalogItem } from '../shopCatalog'
import type { CartItem } from '../../../../shared/cart/cartStorage'

export default function ShopCard({
  item,
  inCart,
  onAdd,
}: {
  item: ShopCatalogItem
  inCart?: CartItem
  onAdd: () => void
}) {
  return (
    <article className="shop-card">
      <div className="shop-card-icon">
        <CatalogItemImage
          src={item.imageUrl}
          alt={item.name}
          fallbackIcon={item.itemType === 'INGREDIENT' ? '🥘' : '⚙️'}
        />
      </div>

      <div className="shop-card-type">
        {item.itemType === 'INGREDIENT'
          ? 'Ingredient'
          : 'Equipment'}
      </div>

      <h3 className="shop-card-title">{item.name}</h3>

      <p className="shop-card-description">
        {item.description || 'No description available'}
      </p>

      <div className="shop-card-bottom">
        <div className="shop-card-price">
          {formatMoney(item.basePrice)}
        </div>

        <button
          className="shop-add-button"
          onClick={onAdd}
        >
          {inCart ? '+ Add one more' : '+ Add to cart'}
        </button>

        {inCart && (
          <div className="shop-cart-chip">
            ✓ {inCart.quantity} in cart
          </div>
        )}
      </div>
    </article>
  )
}
