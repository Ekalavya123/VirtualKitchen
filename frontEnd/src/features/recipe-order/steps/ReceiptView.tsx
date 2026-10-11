import { Link } from 'react-router-dom'
import CancelOrderButton from '../components/CancelOrderButton'
import DemoNotice from '../components/DemoNotice'
import IngredientUsageList from '../components/IngredientUsageList'
import OrderSummaryCard from '../components/OrderSummaryCard'
import { formatDateTime, formatMoney } from '../model/orderFormat'
import { isPaidStatus } from '../model/orderView'
import type { OrderStepProps } from './stepProps'

/**
 * Step 4: the receipt of a paid order (or the summary of a cancelled one) — what was ordered, where
 * it goes, the simulated payment, and the ingredients it takes from the inventory.
 */
export default function ReceiptView({ order, onOrderChange, onNavigate, onError }: OrderStepProps) {
  const paid = isPaidStatus(order.status)
  const cancelled = order.status === 'CANCELLED'
  const payment = order.payment
  const { pricing, demo } = order

  return (
    <div className="ro-stack">
      {cancelled && (
        <div className="ro-alert ro-alert-danger" role="status">
          <strong>This order was cancelled</strong>
          {order.timestamps?.cancelledAt && <> on {formatDateTime(order.timestamps.cancelledAt)}</>}. Any reserved ingredients were
          returned to your inventory.
        </div>
      )}
      {paid && order.status === 'CONFIRMED' && (
        <div className="ro-alert ro-alert-success" role="status">
          <strong>Order confirmed.</strong> Your kitchen has everything it needs — start the simulated preparation from the tracking page.
        </div>
      )}

      <OrderSummaryCard order={order} />

      <div className="ro-columns">
        <section className="ro-card" aria-label="Payment">
          <h3 className="ro-card-title">Payment</h3>
          <dl className="ro-price">
            <div>
              <dt>Status</dt>
              <dd>
                {payment ? (
                  <span className={`ro-badge ro-badge-${payment.status === 'PAID' ? 'success' : payment.status === 'FAILED' ? 'danger' : 'neutral'}`}>
                    {payment.status === 'PAID' ? 'Paid' : payment.status === 'FAILED' ? 'Declined' : 'Refunded'}
                  </span>
                ) : (
                  <span className="ro-muted">Not paid</span>
                )}
              </dd>
            </div>
            {payment?.reference && (
              <div>
                <dt>Reference (simulated)</dt>
                <dd className="ro-mono">{payment.reference}</dd>
              </div>
            )}
            {payment?.processedAt && (
              <div>
                <dt>Paid on</dt>
                <dd>{formatDateTime(payment.processedAt)}</dd>
              </div>
            )}
            <div>
              <dt>Ingredients: from your kitchen inventory</dt>
              <dd>{formatMoney(pricing?.ingredientsCost, pricing?.currency)}</dd>
            </div>
            <div>
              <dt>Preparation fee</dt>
              <dd>{formatMoney(pricing?.preparationFee, pricing?.currency)}</dd>
            </div>
            <div>
              <dt>Delivery fee</dt>
              <dd>{formatMoney(pricing?.deliveryFee, pricing?.currency)}</dd>
            </div>
            <div className="ro-price-total">
              <dt>Total</dt>
              <dd>{formatMoney(payment?.amount ?? pricing?.total, payment?.currency ?? pricing?.currency)}</dd>
            </div>
          </dl>
          <p className="ro-hint">Demo pricing and a simulated payment — nothing was charged.</p>
        </section>

        <section className="ro-card" aria-label="Ingredients">
          <h3 className="ro-card-title">{order.ingredientUsage?.length ? 'Ingredients used' : 'Ingredients from your inventory'}</h3>
          <IngredientUsageList order={order} />
          {paid && demo && (
            <div className="ro-estimate">
              <span className="ro-badge ro-badge-neutral">Demo estimate</span>
              <span>
                About {demo.estimatedPreparationMinutes} min to prepare and {demo.estimatedDeliveryMinutes} min to deliver.
              </span>
            </div>
          )}
        </section>
      </div>

      {paid && (
        <DemoNotice>Simulated preparation — no real chef/kitchen is connected in this prototype.</DemoNotice>
      )}

      <div className="ro-actions">
        <CancelOrderButton order={order} onOrderChange={onOrderChange} onError={onError} />
        <Link to="/kitchen/orders" className="ro-btn ro-btn-ghost">View all orders</Link>
        {paid && (
          <button type="button" className="ro-btn ro-btn-primary" onClick={() => onNavigate('track')}>
            Track order
          </button>
        )}
      </div>
    </div>
  )
}
