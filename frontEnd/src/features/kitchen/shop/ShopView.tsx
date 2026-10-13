import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import ListEnd from '../../../shared/components/ListEnd'
import PaymentGatewayOverlay from '../../../shared/components/payment/PaymentGatewayOverlay'
import { formatMoney } from '../../../shared/format/money'
import { useIncrementalReveal } from '../../../shared/hooks/useIncrementalReveal'
import CartDrawer from './components/CartDrawer'
import FloatingCartButton from './components/FloatingCartButton'
import ShopCard from './components/ShopCard'
import ShopFilters from './components/ShopFilters'
import ShopHeader from './components/ShopHeader'
import ShopSearch from './components/ShopSearch'
import { useShopCatalog } from './shopCatalog'
import { useCart } from './useCart'
import { useCheckout } from './useCheckout'
import './ShopView.css'

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

type ShopFilter = 'all' | 'ingredients' | 'equipment'

/** True while the element given to the returned ref callback is on screen. */
function useOnScreen<T extends Element>() {
  const [onScreen, setOnScreen] = useState(true)
  const observer = useRef<IntersectionObserver | null>(null)
  const ref = useCallback((node: T | null) => {
    observer.current?.disconnect()
    observer.current = null
    if (!node) return
    observer.current = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting))
    observer.current.observe(node)
  }, [])
  useEffect(() => () => observer.current?.disconnect(), [])
  return [ref, onScreen] as const
}

/** The demo shop: browse the catalog, fill a cart kept in this browser, and pay through a simulated gateway. */
export default function ShopView({ userId, onOrderPlaced, resumeRecipeOrder }: ShopViewProps) {
  const catalog = useShopCatalog()
  const cart = useCart(userId)
  const checkout = useCheckout({ userId, items: cart.items, total: cart.total, onPaid: cart.empty, onOrderPlaced })

  // The floating cart button shows once the header's cart button has scrolled away.
  const [headerCartRef, headerCartVisible] = useOnScreen<HTMLButtonElement>()
  const [filter, setFilter] = useState<ShopFilter>('all')
  const [search, setSearch] = useState('')
  // Coming from a recipe order, the cart already holds the missing ingredients: show it.
  const [cartOpen, setCartOpen] = useState(Boolean(resumeRecipeOrder))

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase()
    return catalog.items.filter((item) => {
      const matchesFilter = filter === 'all'
        || (filter === 'ingredients' && item.itemType === 'INGREDIENT')
        || (filter === 'equipment' && item.itemType === 'EQUIPMENT')
      if (!matchesFilter) return false
      return !query || item.name.toLowerCase().includes(query) || item.description?.toLowerCase().includes(query)
    })
  }, [catalog.items, filter, search])

  // Long catalogs render a page at a time, revealing more on scroll.
  const {
    visible: visibleItems,
    hasMore: hasMoreItems,
    sentinelRef: itemsSentinelRef,
    showMore: showMoreItems,
  } = useIncrementalReveal(filteredItems, `${filter}|${search.trim().toLowerCase()}`)

  return (
    <>
      <div className="shop-page">
        <div className="shop-view">
          {resumeRecipeOrder && (
            <div className="shop-resume-banner" role="status">
              <span aria-hidden="true">🧾</span>
              <span className="shop-resume-text">
                Shopping for recipe order{' '}
                <strong>{resumeRecipeOrder.orderCode ?? `#${resumeRecipeOrder.orderId}`}</strong>
                . After checkout you'll go straight back to its ingredient check.
              </span>
              <Link className="shop-resume-link" to={`/kitchen/recipe-orders/${resumeRecipeOrder.orderId}/ingredients`}>
                ← Back to order
              </Link>
            </div>
          )}

          <ShopHeader cartCount={cart.count} onOpenCart={() => setCartOpen(true)} cartButtonRef={headerCartRef} />
          <ShopSearch search={search} onSearchChange={setSearch} />
          <ShopFilters filter={filter} onFilterChange={setFilter} />

          {catalog.loading && catalog.items.length === 0 ? (
            <div className="shop-loading">Loading shop items...</div>
          ) : catalog.error ? (
            <div className="shop-error">{catalog.error}</div>
          ) : filteredItems.length === 0 ? (
            <div className="shop-empty">
              <div className="shop-empty-icon">🔍</div>
              <strong>{search ? 'No matching items' : 'No items available'}</strong>
              <span>{search ? 'Try a different search term.' : 'There are currently no shop items available.'}</span>
            </div>
          ) : (
            <>
              <div className="shop-results-count">
                {filteredItems.length} {filteredItems.length === 1 ? 'item' : 'items'}
              </div>
              <div className="shop-grid">
                {visibleItems.map((item) => (
                  <ShopCard
                    key={`${item.itemType}-${item.id}`}
                    item={item}
                    inCart={cart.lineFor(item)}
                    onAdd={() => {
                      checkout.resetMessages()
                      cart.add(item)
                    }}
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
        cartCount={cart.count}
        cartTotal={cart.total}
        onOpenCart={() => setCartOpen(true)}
      />

      <PaymentGatewayOverlay
        phase={checkout.phase}
        amount={formatMoney(checkout.amount)}
        description="Shop order"
        reference={checkout.reference}
        failureMessage={checkout.error}
        onDone={checkout.finish}
        successLabel={resumeRecipeOrder ? 'Back to recipe order' : 'View my orders'}
      />

      <CartDrawer
        open={cartOpen}
        cartItems={cart.items}
        cartTotal={cart.total}
        checkoutError={checkout.error}
        paymentSuccessMessage={checkout.successMessage}
        processingPayment={checkout.processing}
        onClose={() => setCartOpen(false)}
        onRemove={(item) => void cart.remove(item)}
        onQuantityChange={cart.setQuantity}
        onUnitChange={cart.setUnit}
        onCheckout={() => void checkout.checkout()}
      />
    </>
  )
}
