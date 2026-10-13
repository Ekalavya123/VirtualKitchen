import { useCallback, useEffect, useMemo, useRef, useState, type Ref } from 'react'
import { Link } from 'react-router-dom'
import {
  OrderApi,
  type OrderCreateRequest,
  ShopApi,
} from '../../../api'
import type { ItemType, UnitType } from '../../../types/kitchen'
import {
  clearCart,
  defaultShopPrice,
  getCartItemKey as getItemKey,
  loadCart,
  mergeCartLine,
  saveCart,
  toCartUnit as toOrderUnit,
  type CartItem,
} from '../../../shared/cart/cartStorage'
import CatalogItemImage from '../components/CatalogItemImage'
import { formatMoney } from '../../../shared/format/money'
import { useConfirm } from '../../../shared/components/confirm/confirmContext'
import { useIncrementalReveal } from '../../../shared/hooks/useIncrementalReveal'
import ListEnd from '../../../shared/components/ListEnd'
import PaymentGatewayOverlay, { type PaymentPhase } from '../../../shared/components/payment/PaymentGatewayOverlay'
import { withMinimumDuration } from '../../../shared/components/payment/withMinimumDuration'
import './ShopView.css'

interface ShopItem {
  id: number
  name: string
  itemType: ItemType
  description?: string
  basePrice: number
  defaultUnit?: UnitType
  imageUrl?: string | null
}

interface ShopViewProps {
  userId: number
  onOrderPlaced: () => void
  /**
   * Set when the user came from a recipe order's ingredient check to buy what's missing: the shop
   * shows which order it's shopping for (with a way back), and opens the cart that check filled.
   */
  resumeRecipeOrder?: {
    orderId: number
    orderCode?: string | null
  }
}

const INGREDIENT_UNIT_OPTIONS: UnitType[] = [
  'KG',
  'GRAM',
  'LITER',
  'ML',
  'COUNT',
]

const EQUIPMENT_UNIT_OPTIONS: UnitType[] = ['COUNT']

function ShopHeader({
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
function CartBadge({ count }: { count: number }) {
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
function FloatingCartButton({
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
    unit: UnitType,
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
                        {formatMoney(item.price)} / unit
                      </span>

                      <strong>
                        {formatMoney(subTotal)}
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
                {formatMoney(cartTotal)}
              </strong>
            </div>

            <div className="cart-summary-row cart-summary-total">
              <span>Total</span>
              <strong>
                {formatMoney(cartTotal)}
              </strong>
            </div>

            <button
              className="checkout-button"
              onClick={onCheckout}
              disabled={processingPayment}
            >
              {processingPayment
                ? 'Processing payment...'
                : `Proceed to payment · ${formatMoney(cartTotal)}`}
            </button>

            <button
              className="continue-shopping-button"
              onClick={onClose}
              disabled={processingPayment}
            >
              Continue shopping
            </button>

            <div className="checkout-note">
              Demo payment only. No real payment
              gateway is used and no card details are
              collected.
            </div>
          </div>
        )}
      </aside>
    </div>
  )
}

export default function ShopView({
  userId,
  onOrderPlaced,
  resumeRecipeOrder,
}: ShopViewProps) {
  const [allItems, setAllItems] = useState<ShopItem[]>([])
  // Read from storage on mount (not in an effect), so the persist effect below can never write an
  // empty cart over the stored one before it has been read.
  const [cartItems, setCartItems] = useState<CartItem[]>(
    () => loadCart(userId),
  )

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const [checkoutError, setCheckoutError] = useState('')
  const [paymentSuccessMessage, setPaymentSuccessMessage] =
    useState('')

  const [processingPayment, setProcessingPayment] =
    useState(false)

  // The simulated gateway sheet shown while checkout runs and with its outcome.
  const [paymentPhase, setPaymentPhase] =
    useState<PaymentPhase | null>(null)
  const [paymentAmount, setPaymentAmount] = useState(0)
  const [paymentReference, setPaymentReference] =
    useState<string | null>(null)

  const confirm = useConfirm()

  // The floating cart button shows once the header's cart button has scrolled away.
  const [headerCartVisible, setHeaderCartVisible] =
    useState(true)
  const headerCartObserver =
    useRef<IntersectionObserver | null>(null)
  const headerCartRef = useCallback(
    (node: HTMLButtonElement | null) => {
      headerCartObserver.current?.disconnect()
      headerCartObserver.current = null
      if (!node) return
      headerCartObserver.current = new IntersectionObserver(
        ([entry]) => setHeaderCartVisible(entry.isIntersecting),
      )
      headerCartObserver.current.observe(node)
    },
    [],
  )
  useEffect(
    () => () => headerCartObserver.current?.disconnect(),
    [],
  )

  const [filter, setFilter] = useState<
    'all' | 'ingredients' | 'equipment'
  >('all')

  const [search, setSearch] = useState('')
  // Coming from a recipe order, the cart already holds the missing ingredients: show it.
  const [cartOpen, setCartOpen] = useState(
    Boolean(resumeRecipeOrder),
  )

  /*
   * Persist cart
   */
  useEffect(() => {
    saveCart(userId, cartItems)
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
                basePrice: defaultShopPrice(
                  'INGREDIENT',
                  item.basePrice,
                ),
                defaultUnit: toOrderUnit(
                  item.defaultUnit,
                ),
                imageUrl: item.imageUrl,
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
                basePrice: defaultShopPrice(
                  'EQUIPMENT',
                  item.basePrice,
                ),
                defaultUnit: 'COUNT',
                imageUrl: item.imageUrl,
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

    // One more of an item already in the cart keeps that line's unit.
    setCartItems(current =>
      mergeCartLine(current, {
        itemId: item.id,
        itemType: item.itemType,
        itemName: item.name,
        quantity: 1,
        unit:
          item.itemType === 'EQUIPMENT'
            ? 'COUNT'
            : (item.defaultUnit ?? 'GRAM'),
        price: item.basePrice,
      }),
    )
  }

  const handleQuantityChange = (
    item: CartItem,
    quantity: number,
  ) => {
    const safeQuantity = Number.isFinite(quantity)
      ? quantity
      : 0

    if (safeQuantity <= 0) {
      void handleRemoveItem(item)
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
    unit: UnitType,
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

  const handleRemoveItem = async (item: CartItem) => {
    const confirmed = await confirm({
      eyebrow: 'Remove from cart',
      title: `Remove ${item.itemName}?`,
      message: `${item.itemName} (${item.quantity} ${item.unit}) will be taken out of your cart.`,
      confirmLabel: 'Remove',
    })
    if (!confirmed) return

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
    setPaymentAmount(cartTotal)
    setPaymentReference(null)
    setPaymentPhase('processing')

    try {
      // The backend takes the buyer from the session token; the
      // signed-in user's own id is sent only for older backends.
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

      // The demo gateway: the processing animation always plays for a moment.
      const createdOrder = await withMinimumDuration(
        OrderApi.createOrder(payload),
      )
      const orderLabel =
        createdOrder.orderCode ?? `#${createdOrder.orderId}`

      setPaymentSuccessMessage(
        `Demo payment successful. Order ${orderLabel} created.`,
      )
      setPaymentReference(orderLabel)

      setCartItems([])

      clearCart(userId)

      // The gateway sheet shows the success, then moves on (finishPayment).
      setPaymentPhase('success')
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
      setPaymentPhase('failure')
    } finally {
      setProcessingPayment(false)
    }
  }

  const finishPayment = () => {
    const succeeded = paymentPhase === 'success'
    setPaymentPhase(null)
    if (succeeded) {
      onOrderPlaced()
    }
  }

  // Long catalogs render a page at a time, revealing more on scroll.
  const {
    visible: visibleItems,
    hasMore: hasMoreItems,
    sentinelRef: itemsSentinelRef,
    showMore: showMoreItems,
  } = useIncrementalReveal(
    filteredItems,
    `${filter}|${search.trim().toLowerCase()}`,
  )

  return (
    <>
      <div className="shop-page">
        <div className="shop-view">
          {resumeRecipeOrder && (
            <div className="shop-resume-banner" role="status">
              <span aria-hidden="true">🧾</span>

              <span className="shop-resume-text">
                Shopping for recipe order{' '}
                <strong>
                  {resumeRecipeOrder.orderCode ??
                    `#${resumeRecipeOrder.orderId}`}
                </strong>
                . After checkout you'll go straight back to
                its ingredient check.
              </span>

              <Link
                className="shop-resume-link"
                to={`/kitchen/recipe-orders/${resumeRecipeOrder.orderId}/ingredients`}
              >
                ← Back to order
              </Link>
            </div>
          )}

          <ShopHeader
            cartCount={cartCount}
            onOpenCart={() => setCartOpen(true)}
            cartButtonRef={headerCartRef}
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
                {visibleItems.map(item => (
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

              <ListEnd
                hasMore={hasMoreItems}
                shown={visibleItems.length}
                total={filteredItems.length}
                noun="items"
                sentinelRef={itemsSentinelRef}
                onShowMore={showMoreItems}
              />
            </>
          )}
        </div>
      </div>

      <FloatingCartButton
        visible={!headerCartVisible && !cartOpen}
        cartCount={cartCount}
        cartTotal={cartTotal}
        onOpenCart={() => setCartOpen(true)}
      />

      <PaymentGatewayOverlay
        phase={paymentPhase}
        amount={formatMoney(paymentAmount)}
        description="Shop order"
        reference={paymentReference}
        failureMessage={checkoutError}
        onDone={finishPayment}
        successLabel={
          resumeRecipeOrder
            ? 'Back to recipe order'
            : 'View my orders'
        }
      />

      <CartDrawer
        open={cartOpen}
        cartItems={cartItems}
        cartTotal={cartTotal}
        checkoutError={checkoutError}
        paymentSuccessMessage={paymentSuccessMessage}
        processingPayment={processingPayment}
        onClose={() => setCartOpen(false)}
        onRemove={item => void handleRemoveItem(item)}
        onQuantityChange={handleQuantityChange}
        onUnitChange={handleUnitChange}
        onCheckout={() => void handleCheckout()}
      />
    </>
  )
}