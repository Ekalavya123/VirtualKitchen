import { useEffect, useMemo, useState } from 'react'
import { OrderApi, type OrderCreateRequest, type OrderUnitType, ShopApi } from '../../api'
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

const INGREDIENT_UNIT_OPTIONS: OrderUnitType[] = ['KG', 'GRAM', 'LITER', 'ML', 'COUNT']
const EQUIPMENT_UNIT_OPTIONS: OrderUnitType[] = ['COUNT']

const toOrderUnit = (value: unknown): OrderUnitType => {
  const normalized = String(value ?? '').trim().toUpperCase()
  if (normalized === 'KG') return 'KG'
  if (normalized === 'GRAM') return 'GRAM'
  if (normalized === 'LITER') return 'LITER'
  if (normalized === 'ML') return 'ML'
  return 'COUNT'
}

const getCartStorageKey = (userId: number) => `virtual-kitchen.cart.${userId}`

const getItemKey = (itemType: 'INGREDIENT' | 'EQUIPMENT', itemId: number) => `${itemType}-${itemId}`

export default function InventoryShopView({ userId, onOrderPlaced }: InventoryShopViewProps) {
  const [allItems, setAllItems] = useState<ShopItem[]>([])
  const [cartItems, setCartItems] = useState<CartItem[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [checkoutError, setCheckoutError] = useState('')
  const [paymentSuccessMessage, setPaymentSuccessMessage] = useState('')
  const [processingPayment, setProcessingPayment] = useState(false)
  const [filter, setFilter] = useState<'all' | 'ingredients' | 'equipment'>('all')

  useEffect(() => {
    const rawCart = localStorage.getItem(getCartStorageKey(userId))
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
        .map((entry) => ({
          itemId: Number(entry.itemId),
          itemType: (entry.itemType === 'EQUIPMENT' ? 'EQUIPMENT' : 'INGREDIENT') as CartItem['itemType'],
          itemName: String(entry.itemName ?? ''),
          quantity: Number(entry.quantity),
          unit: toOrderUnit(entry.unit),
          price: Number(entry.price),
        }))
        .filter((entry) => Number.isFinite(entry.itemId) && entry.itemName && entry.quantity > 0 && entry.price >= 0)

      setCartItems(normalized)
    } catch {
      setCartItems([])
    }
  }, [userId])

  useEffect(() => {
    localStorage.setItem(getCartStorageKey(userId), JSON.stringify(cartItems))
  }, [cartItems, userId])

  useEffect(() => {
    const fetchAllItems = async () => {
      try {
        setLoading(true)
        setError('')

        const [ingredients, equipments] = await Promise.all([
          ShopApi.getIngredients(),
          ShopApi.getEquipment(),
        ])

        const normalizedIngredients: ShopItem[] = Array.isArray(ingredients)
          ? ingredients.map((item) => ({
              id: item.id,
              name: item.name,
              itemType: 'INGREDIENT',
              description: item.description || 'High quality ingredient',
              basePrice: item.basePrice ?? 5,
              defaultUnit: toOrderUnit(item.defaultUnit),
            }))
          : []

        const normalizedEquipment: ShopItem[] = Array.isArray(equipments)
          ? equipments.map((item) => ({
              id: item.id,
              name: item.name,
              itemType: 'EQUIPMENT',
              description: item.description || 'Professional kitchen equipment',
              basePrice: item.basePrice ?? 50,
              defaultUnit: 'COUNT',
            }))
          : []

        setAllItems([...normalizedIngredients, ...normalizedEquipment])
      } catch (fetchError) {
        console.error('Error fetching shop items:', fetchError)
        setError(fetchError instanceof Error ? fetchError.message : 'Failed to load shop items')
      } finally {
        setLoading(false)
      }
    }

    void fetchAllItems()
  }, [])

  const filteredItems = useMemo(() => {
    return allItems.filter((item) => {
      if (filter === 'all') return true
      if (filter === 'ingredients') return item.itemType === 'INGREDIENT'
      return item.itemType === 'EQUIPMENT'
    })
  }, [allItems, filter])

  const cartTotal = useMemo(() => {
    return cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0)
  }, [cartItems])

  const getCartItem = (item: ShopItem) => {
    return cartItems.find((cartItem) => getItemKey(cartItem.itemType, cartItem.itemId) === getItemKey(item.itemType, item.id))
  }

  const handleAddToCart = (item: ShopItem) => {
    setCheckoutError('')
    setPaymentSuccessMessage('')

    setCartItems((current) => {
      const key = getItemKey(item.itemType, item.id)
      const existing = current.find((cartItem) => getItemKey(cartItem.itemType, cartItem.itemId) === key)

      if (!existing) {
        return [
          ...current,
          {
            itemId: item.id,
            itemType: item.itemType,
            itemName: item.name,
            quantity: 1,
            unit: item.itemType === 'EQUIPMENT' ? 'COUNT' : (item.defaultUnit ?? 'GRAM'),
            price: item.basePrice,
          },
        ]
      }

      return current.map((cartItem) => {
        if (getItemKey(cartItem.itemType, cartItem.itemId) !== key) {
          return cartItem
        }

        return {
          ...cartItem,
          quantity: cartItem.quantity + 1,
        }
      })
    })
  }

  const handleQuantityChange = (item: CartItem, quantity: number) => {
    const safeQuantity = Number.isFinite(quantity) ? quantity : 0
    if (safeQuantity <= 0) {
      setCartItems((current) => current.filter((cartItem) => getItemKey(cartItem.itemType, cartItem.itemId) !== getItemKey(item.itemType, item.itemId)))
      return
    }

    setCartItems((current) => current.map((cartItem) => {
      if (getItemKey(cartItem.itemType, cartItem.itemId) !== getItemKey(item.itemType, item.itemId)) {
        return cartItem
      }

      return {
        ...cartItem,
        quantity: safeQuantity,
      }
    }))
  }

  const handleUnitChange = (item: CartItem, unit: OrderUnitType) => {
    setCartItems((current) => current.map((cartItem) => {
      if (getItemKey(cartItem.itemType, cartItem.itemId) !== getItemKey(item.itemType, item.itemId)) {
        return cartItem
      }

      return {
        ...cartItem,
        unit,
      }
    }))
  }

  const handleRemoveItem = (item: CartItem) => {
    setCartItems((current) => current.filter((cartItem) => getItemKey(cartItem.itemType, cartItem.itemId) !== getItemKey(item.itemType, item.itemId)))
  }

  const handleCheckout = async () => {
    if (cartItems.length === 0 || processingPayment) {
      return
    }

    setCheckoutError('')
    setPaymentSuccessMessage('')
    setProcessingPayment(true)

    try {
      // Dummy payment flow placeholder. This is intentionally mock logic and can be replaced with a real gateway later.
      await new Promise((resolve) => {
        window.setTimeout(resolve, 1200)
      })

      const payload: OrderCreateRequest = {
        userId,
        items: cartItems.map((item) => ({
          itemId: item.itemId,
          itemType: item.itemType,
          itemName: item.itemName,
          quantity: item.quantity,
          unit: item.unit,
          price: item.price,
        })),
      }

      const createdOrder = await OrderApi.createOrder(payload)
      setPaymentSuccessMessage(`Dummy payment successful. Order #${createdOrder.orderId} created.`)
      setCartItems([])
      localStorage.removeItem(getCartStorageKey(userId))
      onOrderPlaced()
    } catch (checkoutFailure) {
      console.error('Checkout failed:', checkoutFailure)
      setCheckoutError(checkoutFailure instanceof Error ? checkoutFailure.message : 'Checkout failed. Please try again.')
    } finally {
      setProcessingPayment(false)
    }
  }

  return (
    <div className="shop-page-layout">
      <div className="shop-view">
        <div className="shop-header">
          <h2>Shop</h2>
          <p className="shop-subtitle">Select ingredients and equipment, then place a dummy checkout order</p>
        </div>

        <div className="shop-filters">
          <button className={`shop-filter-btn ${filter === 'all' ? 'active' : ''}`} onClick={() => setFilter('all')}>
            All Items
          </button>
          <button className={`shop-filter-btn ${filter === 'ingredients' ? 'active' : ''}`} onClick={() => setFilter('ingredients')}>
            Ingredients
          </button>
          <button className={`shop-filter-btn ${filter === 'equipment' ? 'active' : ''}`} onClick={() => setFilter('equipment')}>
            Equipment
          </button>
        </div>

        {loading && allItems.length === 0 ? (
          <div className="shop-loading">Loading shop items...</div>
        ) : error ? (
          <div className="shop-error">{error}</div>
        ) : filteredItems.length === 0 ? (
          <div className="shop-empty">No items are available.</div>
        ) : (
          <div className="shop-grid">
            {filteredItems.map((item) => {
              const inCart = getCartItem(item)
              return (
                <div key={`${item.itemType}-${item.id}`} className="shop-card">
                  <div className="shop-card-icon">{item.itemType === 'INGREDIENT' ? '🥘' : '⚙️'}</div>
                  <h3 className="shop-card-title">{item.name}</h3>
                  <p className="shop-card-description">{item.description || 'No description available'}</p>
                  <div className="shop-card-price">${item.basePrice.toFixed(2)}</div>
                  <button className="shop-add-button" onClick={() => handleAddToCart(item)}>
                    {inCart ? 'Add one more' : 'Add to cart'}
                  </button>
                  {inCart && <div className="shop-cart-chip">In cart: {inCart.quantity}</div>}
                </div>
              )
            })}
          </div>
        )}
      </div>

      <aside className="cart-panel">
        <h3>Cart</h3>

        {paymentSuccessMessage && <div className="checkout-success">{paymentSuccessMessage}</div>}
        {checkoutError && <div className="checkout-error">{checkoutError}</div>}

        {cartItems.length === 0 ? (
          <p className="cart-empty">Your cart is empty.</p>
        ) : (
          <>
            <div className="cart-items">
              {cartItems.map((item) => {
                const unitOptions = item.itemType === 'EQUIPMENT' ? EQUIPMENT_UNIT_OPTIONS : INGREDIENT_UNIT_OPTIONS
                const subTotal = item.price * item.quantity

                return (
                  <div className="cart-item" key={getItemKey(item.itemType, item.itemId)}>
                    <div className="cart-item-header">
                      <strong>{item.itemName}</strong>
                      <button className="cart-remove-button" onClick={() => handleRemoveItem(item)}>
                        Remove
                      </button>
                    </div>

                    <div className="cart-item-meta">{item.itemType}</div>

                    <div className="cart-item-controls">
                      <label>
                        Qty
                        <input
                          type="number"
                          min={0}
                          step={1}
                          value={item.quantity}
                          onChange={(event) => handleQuantityChange(item, Number(event.target.value))}
                        />
                      </label>

                      <label>
                        Unit
                        <select
                          value={item.unit}
                          onChange={(event) => handleUnitChange(item, toOrderUnit(event.target.value))}
                        >
                          {unitOptions.map((unit) => (
                            <option key={unit} value={unit}>
                              {unit}
                            </option>
                          ))}
                        </select>
                      </label>
                    </div>

                    <div className="cart-item-pricing">
                      <span>Price: ${item.price.toFixed(2)}</span>
                      <span>Subtotal: ${subTotal.toFixed(2)}</span>
                    </div>
                  </div>
                )
              })}
            </div>

            <div className="cart-total">Total: ${cartTotal.toFixed(2)}</div>

            <button className="checkout-button" onClick={handleCheckout} disabled={processingPayment}>
              {processingPayment ? 'Processing dummy payment...' : 'Checkout'}
            </button>
            <div className="checkout-note">Dummy payment only. No real payment gateway is used.</div>
          </>
        )}
      </aside>
    </div>
  )
}
