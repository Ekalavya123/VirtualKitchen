import type { RecipeOrder } from '../../../types/recipeOrder'
import type { RecipeOrderView } from '../model/orderView'

/** What every page of a recipe order gets from RecipeOrderPage. */
export type OrderStepProps = {
  order: RecipeOrder
  /** The signed-in user (for their shop cart). */
  userId: number
  /** Replaces the page's order with a server response — the only way the order ever changes. */
  onOrderChange: (order: RecipeOrder) => void
  onNavigate: (view: RecipeOrderView) => void
  /** Shows the error; on a 409 (stale/conflicting state) also re-fetches the order. */
  onError: (error: unknown) => void
  /** Re-fetches the order from the server. */
  onRefetch: () => Promise<void>
}
