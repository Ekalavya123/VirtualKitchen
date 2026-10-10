import type { ReactNode } from 'react'
import type { RecipeOrder } from '../../../types/recipeOrder'
import { formatAddressLines, formatDateTime, formatServings } from '../model/orderFormat'
import OrderStatusBadge from './OrderStatusBadge'
import OrderThumb from './OrderThumb'

type OrderSummaryCardProps = {
  order: RecipeOrder
  /** Extra content under the facts (e.g. the receipt's payment details). */
  children?: ReactNode
}

/** The order at a glance: recipe, code, date, servings, kitchen, status and delivery address. */
export default function OrderSummaryCard({ order, children }: OrderSummaryCardProps) {
  const recipeName = order.recipe?.name ?? 'Recipe'
  const addressLines = formatAddressLines(order.deliveryAddress)

  return (
    <section className="ro-card ro-summary" aria-label="Order summary">
      <div className="ro-summary-main">
        <OrderThumb src={order.recipe?.thumbnailUrl} alt={recipeName} icon={order.recipe?.fallbackIcon} size="lg" />
        <div className="ro-summary-text">
          <div className="ro-summary-top">
            <span className="ro-code">{order.orderCode}</span>
            <OrderStatusBadge status={order.status} />
          </div>
          <h2 className="ro-summary-title">{recipeName}</h2>
          <dl className="ro-facts">
            <div>
              <dt>Ordered</dt>
              <dd>{formatDateTime(order.timestamps?.createdAt) || '—'}</dd>
            </div>
            <div>
              <dt>Servings</dt>
              <dd>{formatServings(order.servings, order.baseServings)}</dd>
            </div>
            <div>
              <dt>Kitchen</dt>
              <dd>
                {order.kitchenName}
                {order.recipe?.ownerName && <span className="ro-muted"> · recipe by {order.recipe.ownerName}</span>}
              </dd>
            </div>
            <div>
              <dt>Deliver to</dt>
              <dd>{addressLines.length > 0 ? addressLines.join(', ') : '—'}</dd>
            </div>
          </dl>
        </div>
      </div>
      {children}
    </section>
  )
}
