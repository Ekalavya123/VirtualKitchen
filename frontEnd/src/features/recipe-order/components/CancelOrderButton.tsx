import { useState } from 'react'
import { RecipeOrderApi } from '../../../api'
import type { RecipeOrder } from '../../../types/recipeOrder'

type CancelOrderButtonProps = {
  order: RecipeOrder
  onOrderChange: (order: RecipeOrder) => void
  onError: (error: unknown) => void
}

/** "Cancel order" with an inline are-you-sure; renders nothing once the order can't be cancelled. */
export default function CancelOrderButton({ order, onOrderChange, onError }: CancelOrderButtonProps) {
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  if (!order.allowedActions.includes('CANCEL')) return null

  const cancel = async () => {
    setCancelling(true)
    try {
      onOrderChange(await RecipeOrderApi.cancel(order.id))
    } catch (error) {
      onError(error)
    } finally {
      setCancelling(false)
      setConfirming(false)
    }
  }

  if (!confirming) {
    return (
      <button type="button" className="ro-btn ro-btn-danger-ghost" onClick={() => setConfirming(true)}>
        Cancel order
      </button>
    )
  }

  return (
    <div className="ro-cancel-confirm" role="group" aria-label="Confirm cancellation">
      <span>Cancel this order? Any reserved ingredients go back to your inventory.</span>
      <button type="button" className="ro-btn ro-btn-danger" onClick={() => void cancel()} disabled={cancelling}>
        {cancelling ? 'Cancelling…' : 'Yes, cancel'}
      </button>
      <button type="button" className="ro-btn ro-btn-ghost" onClick={() => setConfirming(false)} disabled={cancelling}>
        Keep order
      </button>
    </div>
  )
}
