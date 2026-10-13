import { formatMoney } from '../../../../shared/format/money'
import { EQUIPMENT_UNIT_OPTIONS, INGREDIENT_UNIT_OPTIONS } from '../shopCatalog'
import type { CartItem } from '../../../../shared/cart/cartStorage'
import { getCartItemKey as getItemKey, toCartUnit as toOrderUnit } from '../../../../shared/cart/cartStorage'
import type { UnitType } from '../../../../types/kitchen'

export default function CartDrawer({
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
