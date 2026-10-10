import { useRef, useState } from 'react'
import { ApiError, RecipeOrderApi } from '../../../api'
import type { PaymentMethod } from '../../../types/recipeOrder'
import CancelOrderButton from '../components/CancelOrderButton'
import DemoNotice from '../components/DemoNotice'
import OrderThumb from '../components/OrderThumb'
import { formatMoney, formatQuantity, formatServings } from '../model/orderFormat'
import type { OrderStepProps } from './stepProps'

const METHODS: { id: PaymentMethod; title: string; description: string }[] = [
  { id: 'DEMO_APPROVE', title: 'Demo · Approve payment', description: 'Simulates a successful payment. The order is confirmed and preparation can start.' },
  { id: 'DEMO_DECLINE', title: 'Demo · Decline payment', description: 'Simulates a declined payment. The reserved ingredients are released again.' },
]

/** A key for one payment attempt; crypto.randomUUID needs a secure context, so there's a fallback. */
const newIdempotencyKey = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * Step 3: the simulated payment. Two demo options stand in for a payment provider — nothing is
 * charged and no card details are asked for. A declined payment releases the reservation, so
 * retrying reserves the ingredients again first.
 */
export default function PaymentStep({ order, onOrderChange, onNavigate, onError }: OrderStepProps) {
  const [method, setMethod] = useState<PaymentMethod>('DEMO_APPROVE')
  const [paying, setPaying] = useState(false)
  const [reserving, setReserving] = useState(false)
  const [interrupted, setInterrupted] = useState(false)
  // One key per attempt: re-sent unchanged when an attempt is retried after it never got an answer,
  // so the server can't record it twice; dropped once the server has answered.
  const attemptRef = useRef<{ key: string; method: PaymentMethod } | null>(null)

  const { pricing } = order
  const failed = order.status === 'PAYMENT_FAILED'
  const canPay = order.allowedActions.includes('PAY')
  const lastFailure = failed ? (order.payment ?? order.paymentAttempts?.[order.paymentAttempts.length - 1] ?? null) : null

  const pay = async () => {
    if (!attemptRef.current || attemptRef.current.method !== method) {
      attemptRef.current = { key: newIdempotencyKey(), method }
    }
    setPaying(true)
    setInterrupted(false)
    try {
      const next = await RecipeOrderApi.pay(order.id, method, attemptRef.current.key)
      attemptRef.current = null
      onOrderChange(next)
      if (next.status === 'CONFIRMED') onNavigate('receipt')
    } catch (error) {
      if (error instanceof ApiError) {
        // The server answered (e.g. a conflict): this attempt is settled.
        attemptRef.current = null
        onError(error)
      } else {
        // No answer (offline/timeout): keep the key so "Try again" repeats this same attempt.
        setInterrupted(true)
        onError(error)
      }
    } finally {
      setPaying(false)
    }
  }

  const reserveAgain = async () => {
    setReserving(true)
    try {
      onOrderChange(await RecipeOrderApi.reserve(order.id))
    } catch (error) {
      onError(error)
    } finally {
      setReserving(false)
    }
  }

  return (
    <div className="ro-stack">
      <DemoNotice variant="strong" icon="🧪">
        Simulated payment — no real payment is made and no card details are collected.
      </DemoNotice>

      <div className="ro-columns ro-columns-payment">
        <section className="ro-card" aria-label="Order summary">
          <div className="ro-recipe-mini">
            <OrderThumb src={order.recipe?.thumbnailUrl} alt={order.recipe?.name ?? 'Recipe'} icon={order.recipe?.fallbackIcon} size="sm" />
            <div>
              <strong>{order.recipe?.name ?? 'Recipe'}</strong>
              <div className="ro-muted">{formatServings(order.servings, order.baseServings)} · {order.kitchenName}</div>
            </div>
          </div>

          {order.requirements.length > 0 && (
            <>
              <h4 className="ro-subtitle">Ingredients (reserved from your inventory)</h4>
              <ul className="ro-mini-list">
                {order.requirements.map((requirement) => (
                  <li key={requirement.ingredientId}>
                    <span>{requirement.icon || '🥕'} {requirement.name}</span>
                    <span className="ro-muted">{formatQuantity(requirement.requiredQuantity, requirement.unit)}</span>
                  </li>
                ))}
              </ul>
            </>
          )}

          <div className="ro-price-head">
            <h4 className="ro-subtitle">Price</h4>
            {pricing?.demo !== false && <span className="ro-badge ro-badge-neutral">Demo pricing</span>}
          </div>
          <dl className="ro-price">
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
              <dd>{formatMoney(pricing?.total, pricing?.currency)}</dd>
            </div>
          </dl>
        </section>

        <section className="ro-card" aria-label="Payment">
          {failed ? (
            <div className="ro-stack">
              <div className="ro-alert ro-alert-danger" role="alert">
                <strong>Payment declined — reserve again &amp; retry.</strong>
                <p>
                  {(lastFailure?.failureReason?.trim() || 'The demo payment was declined').replace(/\.?$/, '.')} Your reserved ingredients were released, so they need
                  reserving again before another payment attempt.
                </p>
              </div>
              <div className="ro-actions">
                <button type="button" className="ro-btn ro-btn-ghost" onClick={() => onNavigate('ingredients')}>
                  Check ingredients
                </button>
                <CancelOrderButton order={order} onOrderChange={onOrderChange} onError={onError} />
                {order.allowedActions.includes('RESERVE') && (
                  <button type="button" className="ro-btn ro-btn-primary" onClick={() => void reserveAgain()} disabled={reserving}>
                    {reserving ? 'Reserving…' : 'Reserve ingredients again & retry'}
                  </button>
                )}
              </div>
            </div>
          ) : (
            <div className="ro-stack">
              <fieldset className="ro-methods" disabled={paying || !canPay}>
                <legend className="ro-card-title">Choose a demo outcome</legend>
                {METHODS.map((option) => (
                  <label key={option.id} className={`ro-method${method === option.id ? ' ro-method-selected' : ''}`}>
                    <input
                      type="radio"
                      name="ro-payment-method"
                      value={option.id}
                      checked={method === option.id}
                      onChange={() => setMethod(option.id)}
                    />
                    <span>
                      <strong>{option.title}</strong>
                      <span className="ro-muted">{option.description}</span>
                    </span>
                  </label>
                ))}
              </fieldset>

              {interrupted && (
                <div className="ro-alert ro-alert-warning" role="status">
                  The payment request didn't get an answer. Trying again repeats the same attempt, so it can't be recorded twice.
                </div>
              )}

              <div className="ro-actions">
                <CancelOrderButton order={order} onOrderChange={onOrderChange} onError={onError} />
                <button type="button" className="ro-btn ro-btn-primary" onClick={() => void pay()} disabled={paying || !canPay}>
                  {paying ? 'Processing…' : interrupted ? 'Try again' : `Confirm payment · ${formatMoney(pricing?.total, pricing?.currency)}`}
                </button>
              </div>
              {!canPay && (
                <p className="ro-hint ro-actions-hint">This order can't be paid right now.</p>
              )}
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
