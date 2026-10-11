import type { RecipeOrderStatus } from '../../../types/recipeOrder'
import { getStatusDisplay } from '../model/orderFormat'
import '../recipeOrder.css'

/** A recipe order's status as a coloured pill. */
export default function OrderStatusBadge({ status }: { status: RecipeOrderStatus }) {
  const { label, tone } = getStatusDisplay(status)
  return <span className={`ro-badge ro-badge-${tone}`}>{label}</span>
}
