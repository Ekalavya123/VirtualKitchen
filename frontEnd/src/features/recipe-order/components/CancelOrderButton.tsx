import { useState } from 'react'
import { RecipeOrderApi } from '../../../api'
import type { RecipeOrder } from '../../../types/recipeOrder'
import { useConfirm } from '../../../shared/components/confirm/confirmContext'

type CancelOrderButtonProps = {
  order: RecipeOrder
  onOrderChange: (order: RecipeOrder) => void
  onError: (error: unknown) => void
}

/** "Cancel order" behind the app's confirmation dialog; renders nothing once the order can't be cancelled. */
export default function CancelOrderButton({ order, onOrderChange, onError }: CancelOrderButtonProps) {
  const [cancelling, setCancelling] = useState(false)
  const confirm = useConfirm()

  if (!order.allowedActions.includes('CANCEL')) return null

  const cancel = async () => {
    const paid = order.payment?.status === 'PAID'
    const confirmed = await confirm({
      eyebrow: 'Cancel order',
      title: `Cancel order ${order.orderCode}?`,
      message: `Any ingredients reserved for it go back to your kitchen inventory${
        paid ? ' and the demo payment is refunded' : ''
      }. This cannot be undone.`,
      confirmLabel: 'Cancel order',
      cancelLabel: 'Keep order',
    })
    if (!confirmed) return

    setCancelling(true)
    try {
      onOrderChange(await RecipeOrderApi.cancel(order.id))
    } catch (error) {
      onError(error)
    } finally {
      setCancelling(false)
    }
  }

  return (
    <button type="button" className="ro-btn ro-btn-danger-ghost" onClick={() => void cancel()} disabled={cancelling}>
      {cancelling ? 'Cancelling…' : 'Cancel order'}
    </button>
  )
}
