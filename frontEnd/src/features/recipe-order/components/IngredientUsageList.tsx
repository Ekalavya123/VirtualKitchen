import type { AllocationState, RecipeOrder } from '../../../types/recipeOrder'
import { formatQuantity } from '../model/orderFormat'

const ALLOCATION_LABEL: Partial<Record<AllocationState, { label: string; tone: string }>> = {
  RESERVED: { label: 'Reserved', tone: 'info' },
  CONSUMED: { label: 'Used', tone: 'success' },
  RELEASED: { label: 'Released', tone: 'neutral' },
  FAILED: { label: 'Not reserved', tone: 'danger' },
}

/**
 * What the order takes from the kitchen inventory: once completed, what was actually used
 * (`ingredientUsage`); before that, each requirement and whether it's currently reserved.
 */
export default function IngredientUsageList({ order }: { order: RecipeOrder }) {
  if (order.ingredientUsage && order.ingredientUsage.length > 0) {
    return (
      <ul className="ro-mini-list">
        {order.ingredientUsage.map((line) => (
          <li key={line.ingredientId}>
            <span>{line.ingredientName}</span>
            <span className="ro-mini-list-end">
              <span className="ro-muted">{formatQuantity(line.quantity, line.unit)}</span>
              <span className="ro-badge ro-badge-success">Used</span>
            </span>
          </li>
        ))}
      </ul>
    )
  }

  if (order.requirements.length === 0) {
    return <div className="ro-empty">No catalog ingredients are needed for this recipe.</div>
  }

  return (
    <ul className="ro-mini-list">
      {order.requirements.map((requirement) => {
        const state = order.allocations.find((allocation) => allocation.ingredientId === requirement.ingredientId)?.state
        const display = state ? ALLOCATION_LABEL[state] : undefined
        return (
          <li key={requirement.ingredientId}>
            <span>{requirement.icon || '🥕'} {requirement.name}</span>
            <span className="ro-mini-list-end">
              <span className="ro-muted">{formatQuantity(requirement.requiredQuantity, requirement.unit)}</span>
              {display && <span className={`ro-badge ro-badge-${display.tone}`}>{display.label}</span>}
            </span>
          </li>
        )
      })}
    </ul>
  )
}
