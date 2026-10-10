import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { RecipeDetailApi, RecipeOrderApi } from '../../api'
import { useNotifications } from '../../shared/components/notifications/NotificationProvider'
import { recipeOrderPath } from './model/orderView'

/**
 * "Order Recipe": starts a draft order for one of the user's recipes and opens its confirm page.
 * The order is for the recipe's own serving count when it has one (1 otherwise); `knownServings`
 * skips looking it up when the caller already has the recipe's nutrition. Returns the id of the
 * recipe currently being ordered, so its button can show progress and ignore repeat clicks.
 */
export function useStartRecipeOrder() {
  const navigate = useNavigate()
  const { notifyError } = useNotifications()
  const [orderingRecipeId, setOrderingRecipeId] = useState<number | null>(null)

  const startOrder = useCallback(async (recipeId: number, knownServings?: number | null) => {
    if (orderingRecipeId != null) return
    setOrderingRecipeId(recipeId)
    try {
      let servings = knownServings ?? undefined
      if (servings == null) {
        // Only the detail endpoint carries nutrition; failing to read it just means 1 serving.
        servings = await RecipeDetailApi.getRecipeDetail(recipeId)
          .then((detail) => detail.nutrition?.servings ?? undefined)
          .catch(() => undefined)
      }
      const order = await RecipeOrderApi.create({ recipeId, servings: servings && servings > 0 ? servings : 1 })
      navigate(recipeOrderPath(order.id, 'confirm'))
    } catch (error) {
      notifyError(error instanceof Error ? error.message : 'Unable to start the order')
    } finally {
      setOrderingRecipeId(null)
    }
  }, [navigate, notifyError, orderingRecipeId])

  return { startOrder, orderingRecipeId }
}
