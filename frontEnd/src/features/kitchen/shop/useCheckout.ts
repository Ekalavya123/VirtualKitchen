import { useState } from 'react'
import { OrderApi, type OrderCreateRequest } from '../../../api'
import type { CartItem } from '../../../shared/cart/cartStorage'
import type { PaymentPhase } from '../../../shared/components/payment/PaymentGatewayOverlay'
import { withMinimumDuration } from '../../../shared/components/payment/withMinimumDuration'
import { errorMessage } from '../../../shared/errors'

/**
 * The shop's demo checkout: places the cart as an order behind the simulated payment gateway sheet
 * (`phase`), empties the cart on success, and calls `onOrderPlaced` once the user dismisses the sheet.
 */
export function useCheckout({ userId, items, total, onPaid, onOrderPlaced }: {
  userId: number
  items: CartItem[]
  total: number
  /** The order went through: empty the cart. */
  onPaid: () => void
  onOrderPlaced: () => void
}) {
  const [processing, setProcessing] = useState(false)
  const [phase, setPhase] = useState<PaymentPhase | null>(null)
  const [amount, setAmount] = useState(0)
  const [reference, setReference] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')

  /** Clears the last checkout's outcome (e.g. once the cart changes). */
  const resetMessages = () => {
    setError('')
    setSuccessMessage('')
  }

  const checkout = async () => {
    if (items.length === 0 || processing) return
    resetMessages()
    setProcessing(true)
    setAmount(total)
    setReference(null)
    setPhase('processing')
    try {
      // The backend takes the buyer from the session token; the signed-in user's own id is sent only
      // for older backends.
      const payload: OrderCreateRequest = {
        userId,
        items: items.map((item) => ({
          itemId: item.itemId,
          itemType: item.itemType,
          itemName: item.itemName,
          quantity: item.quantity,
          unit: item.unit,
          price: item.price,
        })),
      }
      // The demo gateway: the processing animation always plays for a moment.
      const createdOrder = await withMinimumDuration(OrderApi.createOrder(payload))
      const orderLabel = createdOrder.orderCode ?? `#${createdOrder.orderId}`
      setSuccessMessage(`Demo payment successful. Order ${orderLabel} created.`)
      setReference(orderLabel)
      onPaid()
      // The gateway sheet shows the success, then moves on (finish).
      setPhase('success')
    } catch (failure) {
      setError(errorMessage(failure, 'Checkout failed. Please try again.'))
      setPhase('failure')
    } finally {
      setProcessing(false)
    }
  }

  /** The user dismissed the gateway sheet: move on after a success, stay in the shop after a failure. */
  const finish = () => {
    const succeeded = phase === 'success'
    setPhase(null)
    if (succeeded) onOrderPlaced()
  }

  return { processing, phase, amount, reference, error, successMessage, resetMessages, checkout, finish }
}
