import { useEffect, useMemo, useState } from 'react'
import {
  OrderApi,
  type OrderCreateRequest,
  type OrderUnitType,
  ShopApi,
} from '../../api'
import './InventoryShopView.css'

interface ShopItem {
  id: number
  name: string
  itemType: 'INGREDIENT' | 'EQUIPMENT'
  description?: string
  basePrice: number
  defaultUnit?: OrderUnitType
}

interface CartItem {
  itemId: number
  itemType: 'INGREDIENT' | 'EQUIPMENT'
  itemName: string
  quantity: number
  unit: OrderUnitType
  price: number
}

interface InventoryShopViewProps {
  userId: number
  onOrderPlaced: () => void
}

const INGREDIENT_UNIT_OPTIONS: OrderUnitType[] = [
  'KG',
  'GRAM',
  'LITER',
  'ML',
  'COUNT',
]

const EQUIPMENT_UNIT_OPTIONS: OrderUnitType[] = ['COUNT']

const toOrderUnit = (value: unknown): OrderUnitType => {
  const normalized = String(value ?? '').trim().toUpperCase()

  if (normalized === 'KG') return 'KG'
  if (normalized === 'GRAM') return 'GRAM'
  if (normalized === 'LITER') return 'LITER'
  if (normalized === 'ML') return 'ML'

  return 'COUNT'
}

const getCartStorageKey = (userId: number) =>
  `virtual-kitchen.cart.${userId}`

const getItemKey = (
  itemType: 'INGREDIENT' | 'EQUIPMENT',
  itemId: number,
) => `${itemType}-${itemId}`

function ShopHeader({
  cartCount,
  onOpenCart,
}: {
  cartCount: number
  onOpenCart: () => void
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
        className="shop-cart-button"
        onClick={onOpenCart}
        aria-label={`Open cart with ${cartCount} items`}
      >
        <span className="shop-cart-icon" aria-hidden="true">
          🛒
        </span>

        <span className="shop-cart-label">Cart</span>

        {cartCount > 0 && (
          <span className="shop-cart-badge">
            {cartCount > 99 ? '99+' : cartCount}
          </span>
        )}
      </button>
    </header>
  )
}

function ShopSearch({
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

function ShopFilters({
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

function ShopCard({
  item,
  inCart,
  onAdd,
}: {
  item: ShopItem
  inCart?: CartItem
  onAdd: () => void
}) {
  return (
    <article className="shop-card">
      <div className="shop-card-icon" aria-hidden="true">
        {item.itemType === 'INGREDIENT' ? '🥘' : '⚙️'}
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
          ${item.basePrice.toFixed(2)}
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

function CartDrawer({
  open,
  cartItems,
  cartTotal,
  checkoutError,
  paymentSuccessMessage,
  processingPayment,
  onClose,
  onRemove,
  onQuantityChange,
  onUnitChange,
  onCheckout,
}: {
  open: boolean
  cartItems: CartItem[]
  cartTotal: number
  checkoutError: string
  paymentSuccessMessage: string
  processingPayment: boolean
  onClose: () => void
  onRemove: (item: CartItem) => void
  onQuantityChange: (
    item: CartItem,
    quantity: number,
  ) => void
  onUnitChange: (
    item: CartItem,
    unit: OrderUnitType,
  ) => void
  onCheckout: () => void
}) {
  if (!open) {
    return null
  }

  return (
    <div
      className="cart-overlay"
      onMouseDown={event => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <aside
        className="cart-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-title"
      >
        <div className="cart-drawer-header">
          <div>
            <div className="cart-drawer-eyebrow">
              Your selection
            </div>

            <h2 id="cart-title">Shopping Cart</h2>

            <p>
              {cartItems.length === 0
                ? 'Your cart is empty'
                : `${cartItems.length} ${
                    cartItems.length === 1
                      ? 'item'
                      : 'different items'
                  }`}
            </p>
          </div>

          <button
            className="cart-drawer-close"
            onClick={onClose}
            aria-label="Close cart"
          >
            ✕
          </button>
        </div>

        <div className="cart-drawer-body">
          {paymentSuccessMessage && (
            <div className="checkout-success">
              {paymentSuccessMessage}
            </div>
          )}

          {checkoutError && (
            <div className="checkout-error">
              {checkoutError}
            </div>
          )}

          {cartItems.length === 0 ? (
            <div className="cart-empty-state">
              <div className="cart-empty-icon">🛒</div>

              <h3>Your cart is empty</h3>

              <p>
                Add ingredients or equipment from the shop
                to continue.
              </p>

              <button
                className="cart-continue-button"
                onClick={onClose}
              >
                Continue shopping
              </button>
            </div>
          ) : (
            <div className="cart-items">
              {cartItems.map(item => {
                const unitOptions =
                  item.itemType === 'EQUIPMENT'
                    ? EQUIPMENT_UNIT_OPTIONS
                    : INGREDIENT_UNIT_OPTIONS

                const subTotal =
                  item.price * item.quantity

                return (
                  <div
                    className="cart-item"
                    key={getItemKey(
                      item.itemType,
                      item.itemId,
                    )}
                  >
                    <div className="cart-item-top">
                      <div>
                        <div className="cart-item-name">
                          {item.itemName}
                        </div>

                        <div className="cart-item-meta">
                          {item.itemType === 'INGREDIENT'
                            ? 'Ingredient'
                            : 'Equipment'}
                        </div>
                      </div>

                      <button
                        className="cart-remove-button"
                        onClick={() => onRemove(item)}
                      >
                        Remove
                      </button>
                    </div>

                    <div className="cart-item-controls">
                      <label>
                        Quantity

                        <input
                          type="number"
                          min={0}
                          step={1}
                          value={item.quantity}
                          onChange={event =>
                            onQuantityChange(
                              item,
                              Number(event.target.value),
                            )
                          }
                        />
                      </label>

                      <label>
                        Unit

                        <select
                          value={item.unit}
                          onChange={event =>
                            onUnitChange(
                              item,
                              toOrderUnit(
                                event.target.value,
                              ),
                            )
                          }
                        >
                          {unitOptions.map(unit => (
                            <option
                              key={unit}
                              value={unit}
                            >
                              {unit}
                            </option>
                          ))}
                        </select>
                      </label>
                    </div>

                    <div className="cart-item-pricing">
                      <span>
                        ${item.price.toFixed(2)} / unit
                      </span>

                      <strong>
                        ${subTotal.toFixed(2)}
                      </strong>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>

        {cartItems.length > 0 && (
          <div className="cart-drawer-footer">
            <div className="cart-summary-row">
              <span>Subtotal</span>
              <strong>
                ${cartTotal.toFixed(2)}
              </strong>
            </div>

            <div className="cart-summary-row cart-summary-total">
              <span>Total</span>
              <strong>
                ${cartTotal.toFixed(2)}
              </strong>
            </div>

            <button
              className="checkout-button"
              onClick={onCheckout}
              disabled={processingPayment}
            >
              {processingPayment
                ? 'Processing payment...'
                : `Proceed to payment · $${cartTotal.toFixed(
                    2,
                  )}`}
            </button>

            <button
              className="continue-shopping-button"
              onClick={onClose}
              disabled={processingPayment}
            >
              Continue shopping
            </button>

            <div className="checkout-note">
              Dummy payment only. No real payment
              gateway is used.
            </div>
          </div>
        )}
      </aside>
    </div>
  )
}

export default function InventoryShopView({
  userId,
  onOrderPlaced,
}: InventoryShopViewProps) {
  const [allItems, setAllItems] = useState<ShopItem[]>([])
  const [cartItems, setCartItems] = useState<CartItem[]>([])

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [checkoutError, setCheckoutError] = useState('')
  const [paymentSuccessMessage, setPaymentSuccessMessage] =
    useState('')

  const [processingPayment, setProcessingPayment] =
    useState(false)

  const [filter, setFilter] = useState<
    'all' | 'ingredients' | 'equipment'
  >('all')

  const [search, setSearch] = useState('')
  const [cartOpen, setCartOpen] = useState(false)

  /*
   * Load persisted cart
   */
  useEffect(() => {
    const rawCart = localStorage.getItem(
      getCartStorageKey(userId),
    )

    if (!rawCart) {
      setCartItems([])
      return
    }

    try {
      const parsed = JSON.parse(rawCart)

      if (!Array.isArray(parsed)) {
        setCartItems([])
        return
      }

      const normalized = parsed
        .map(entry => ({
          itemId: Number(entry.itemId),
          itemType:
            (entry.itemType === 'EQUIPMENT'
              ? 'EQUIPMENT'
              : 'INGREDIENT') as CartItem['itemType'],
          itemName: String(entry.itemName ?? ''),
          quantity: Number(entry.quantity),
          unit: toOrderUnit(entry.unit),
          price: Number(entry.price),
        }))
        .filter(
          entry =>
            Number.isFinite(entry.itemId) &&
            entry.itemName &&
            entry.quantity > 0 &&
            entry.price >= 0,
        )

      setCartItems(normalized)
    } catch {
      setCartItems([])
    }
  }, [userId])

  /*
   * Persist cart
   */
  useEffect(() => {
    localStorage.setItem(
      getCartStorageKey(userId),
      JSON.stringify(cartItems),
    )
  }, [cartItems, userId])

  /*
   * Fetch shop items
   */
  useEffect(() => {
    const fetchAllItems = async () => {
      try {
        setLoading(true)
        setError('')

        const [ingredients, equipments] =
          await Promise.all([
            ShopApi.getIngredients(),
            ShopApi.getEquipment(),
          ])

        const normalizedIngredients: ShopItem[] =
          Array.isArray(ingredients)
            ? ingredients.map(item => ({
                id: item.id,
                name: item.name,
                itemType: 'INGREDIENT',
                description:
                  item.description ||
                  'High quality ingredient',
                basePrice: item.basePrice ?? 5,
                defaultUnit: toOrderUnit(
                  item.defaultUnit,
                ),
              }))
            : []

        const normalizedEquipment: ShopItem[] =
          Array.isArray(equipments)
            ? equipments.map(item => ({
                id: item.id,
                name: item.name,
                itemType: 'EQUIPMENT',
                description:
                  item.description ||
                  'Professional kitchen equipment',
                basePrice: item.basePrice ?? 50,
                defaultUnit: 'COUNT',
              }))
            : []

        setAllItems([
          ...normalizedIngredients,
          ...normalizedEquipment,
        ])
      } catch (fetchError) {
        console.error(
          'Error fetching shop items:',
          fetchError,
        )

        setError(
          fetchError instanceof Error
            ? fetchError.message
            : 'Failed to load shop items',
        )
      } finally {
        setLoading(false)
      }
    }

    void fetchAllItems()
  }, [])

  /*
   * Search + category filtering
   */
  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase()

    return allItems.filter(item => {
      const matchesFilter =
        filter === 'all' ||
        (filter === 'ingredients' &&
          item.itemType === 'INGREDIENT') ||
        (filter === 'equipment' &&
          item.itemType === 'EQUIPMENT')

      if (!matchesFilter) {
        return false
      }

      if (!query) {
        return true
      }

      return (
        item.name.toLowerCase().includes(query) ||
        item.description
          ?.toLowerCase()
          .includes(query)
      )
    })
  }, [allItems, filter, search])

  const cartTotal = useMemo(
    () =>
      cartItems.reduce(
        (sum, item) =>
          sum + item.price * item.quantity,
        0,
      ),
    [cartItems],
  )

  const cartCount = useMemo(
    () =>
      cartItems.reduce(
        (sum, item) => sum + item.quantity,
        0,
      ),
    [cartItems],
  )

  const getCartItem = (item: ShopItem) =>
    cartItems.find(
      cartItem =>
        getItemKey(
          cartItem.itemType,
          cartItem.itemId,
        ) === getItemKey(item.itemType, item.id),
    )

  const handleAddToCart = (item: ShopItem) => {
    setCheckoutError('')
    setPaymentSuccessMessage('')

    setCartItems(current => {
      const key = getItemKey(
        item.itemType,
        item.id,
      )

      const existing = current.find(
        cartItem =>
          getItemKey(
            cartItem.itemType,
            cartItem.itemId,
          ) === key,
      )

      if (!existing) {
        return [
          ...current,
          {
            itemId: item.id,
            itemType: item.itemType,
            itemName: item.name,
            quantity: 1,
            unit:
              item.itemType === 'EQUIPMENT'
                ? 'COUNT'
                : (item.defaultUnit ?? 'GRAM'),
            price: item.basePrice,
          },
        ]
      }

      return current.map(cartItem => {
        if (
          getItemKey(
            cartItem.itemType,
            cartItem.itemId,
          ) !== key
        ) {
          return cartItem
        }

        return {
          ...cartItem,
          quantity: cartItem.quantity + 1,
        }
      })
    })
  }

  const handleQuantityChange = (
    item: CartItem,
    quantity: number,
  ) => {
    const safeQuantity = Number.isFinite(quantity)
      ? quantity
      : 0

    if (safeQuantity <= 0) {
      handleRemoveItem(item)
      return
    }

    setCartItems(current =>
      current.map(cartItem => {
        if (
          getItemKey(
            cartItem.itemType,
            cartItem.itemId,
          ) !==
          getItemKey(
            item.itemType,
            item.itemId,
          )
        ) {
          return cartItem
        }

        return {
          ...cartItem,
          quantity: safeQuantity,
        }
      }),
    )
  }

  const handleUnitChange = (
    item: CartItem,
    unit: OrderUnitType,
  ) => {
    setCartItems(current =>
      current.map(cartItem => {
        if (
          getItemKey(
            cartItem.itemType,
            cartItem.itemId,
          ) !==
          getItemKey(
            item.itemType,
            item.itemId,
          )
        ) {
          return cartItem
        }

        return {
          ...cartItem,
          unit,
        }
      }),
    )
  }

  const handleRemoveItem = (item: CartItem) => {
    setCartItems(current =>
      current.filter(
        cartItem =>
          getItemKey(
            cartItem.itemType,
            cartItem.itemId,
          ) !==
          getItemKey(
            item.itemType,
            item.itemId,
          ),
      ),
    )
  }

  const handleCheckout = async () => {
    if (
      cartItems.length === 0 ||
      processingPayment
    ) {
      return
    }

    setCheckoutError('')
    setPaymentSuccessMessage('')
    setProcessingPayment(true)

    try {
      // Dummy payment flow placeholder.
      await new Promise(resolve => {
        window.setTimeout(resolve, 1200)
      })

      const payload: OrderCreateRequest = {
        userId,
        items: cartItems.map(item => ({
          itemId: item.itemId,
          itemType: item.itemType,
          itemName: item.itemName,
          quantity: item.quantity,
          unit: item.unit,
          price: item.price,
        })),
      }

      const createdOrder =
        await OrderApi.createOrder(payload)

      setPaymentSuccessMessage(
        `Dummy payment successful. Order #${createdOrder.orderId} created.`,
      )

      setCartItems([])

      localStorage.removeItem(
        getCartStorageKey(userId),
      )

      onOrderPlaced()

      // Keep the drawer open so the success message
      // can be seen.
    } catch (checkoutFailure) {
      console.error(
        'Checkout failed:',
        checkoutFailure,
      )

      setCheckoutError(
        checkoutFailure instanceof Error
          ? checkoutFailure.message
          : 'Checkout failed. Please try again.',
      )
    } finally {
      setProcessingPayment(false)
    }
  }

  return (
    <>
      <div className="shop-page">
        <div className="shop-view">
          <ShopHeader
            cartCount={cartCount}
            onOpenCart={() => setCartOpen(true)}
          />

          <ShopSearch
            search={search}
            onSearchChange={setSearch}
          />

          <ShopFilters
            filter={filter}
            onFilterChange={setFilter}
          />

          {loading && allItems.length === 0 ? (
            <div className="shop-loading">
              Loading shop items...
            </div>
          ) : error ? (
            <div className="shop-error">{error}</div>
          ) : filteredItems.length === 0 ? (
            <div className="shop-empty">
              <div className="shop-empty-icon">
                🔍
              </div>

              <strong>
                {search
                  ? 'No matching items'
                  : 'No items available'}
              </strong>

              <span>
                {search
                  ? 'Try a different search term.'
                  : 'There are currently no shop items available.'}
              </span>
            </div>
          ) : (
            <>
              <div className="shop-results-count">
                {filteredItems.length}{' '}
                {filteredItems.length === 1
                  ? 'item'
                  : 'items'}
              </div>

              <div className="shop-grid">
                {filteredItems.map(item => (
                  <ShopCard
                    key={`${item.itemType}-${item.id}`}
                    item={item}
                    inCart={getCartItem(item)}
                    onAdd={() =>
                      handleAddToCart(item)
                    }
                  />
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      <CartDrawer
        open={cartOpen}
        cartItems={cartItems}
        cartTotal={cartTotal}
        checkoutError={checkoutError}
        paymentSuccessMessage={paymentSuccessMessage}
        processingPayment={processingPayment}
        onClose={() => setCartOpen(false)}
        onRemove={handleRemoveItem}
        onQuantityChange={handleQuantityChange}
        onUnitChange={handleUnitChange}
        onCheckout={() => void handleCheckout()}
      />
    </>
  )
}