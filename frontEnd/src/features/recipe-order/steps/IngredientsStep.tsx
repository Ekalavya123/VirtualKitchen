import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { RecipeOrderApi, httpStatusOf } from '../../../api'
import type { AvailabilityLine, AvailabilityReport } from '../../../types/recipeOrder'
import { defaultShopPrice, loadCart, mergeCartLine, saveCart, type ShopResumeState } from '../../../shared/cart/cartStorage'
import { recipeToolPath } from '../../recipe-tool/recipeToolRoutes'
import CancelOrderButton from '../components/CancelOrderButton'
import { formatQuantity } from '../model/orderFormat'
import type { OrderStepProps } from './stepProps'

function BasisBadge({ line }: { line: AvailabilityLine }) {
  if (line.basis !== 'DEFAULT_FACTOR') return null
  const notes = line.conversionNotes ?? []
  return (
    <details className="ro-basis">
      <summary className="ro-basis-badge" title={notes.join('\n') || undefined}>≈ estimated conversion</summary>
      {notes.length > 0 ? (
        <ul className="ro-basis-notes">
          {notes.map((note) => <li key={note}>{note}</li>)}
        </ul>
      ) : (
        <p className="ro-basis-notes">Converted with a typical density/weight for this ingredient.</p>
      )}
    </details>
  )
}

function AvailabilityRow({ line }: { line: AvailabilityLine }) {
  const short = !line.sufficient
  return (
    <li className={`ro-avail-row${short ? ' ro-avail-short' : ''}`}>
      <div className="ro-avail-name">
        <span className="ro-avail-icon" aria-hidden>
          {line.imageUrl ? <img src={line.imageUrl} alt="" loading="lazy" /> : line.icon || '🥕'}
        </span>
        <div>
          <strong>{line.name}</strong>
          <div className="ro-avail-status">{short ? <span className="ro-badge ro-badge-danger">Short</span> : <span className="ro-badge ro-badge-success">In stock</span>}</div>
        </div>
      </div>
      <dl className="ro-avail-figures">
        <div>
          <dt>Required</dt>
          <dd>{formatQuantity(line.required, line.unit)}</dd>
        </div>
        <div>
          <dt>In your kitchen</dt>
          <dd>{formatQuantity(line.available, line.unit)}</dd>
        </div>
        <div>
          <dt>Missing</dt>
          <dd className={short ? 'ro-text-danger' : undefined}>{short ? formatQuantity(line.missing, line.unit) : '—'}</dd>
        </div>
        <div>
          <dt>{line.reservedForOrder > 0 ? 'Reserved' : 'Will be reserved'}</dt>
          <dd>{line.reservedForOrder > 0 ? formatQuantity(line.reservedForOrder, line.unit) : line.sufficient ? formatQuantity(line.required, line.unit) : '—'}</dd>
        </div>
      </dl>
      <div className="ro-avail-extra">
        <BasisBadge line={line} />
        {short && line.purchase && (
          <span className="ro-purchase-hint">
            Needed for recipe: {formatQuantity(line.purchase.neededQuantity, line.purchase.neededUnit)} · Sold in shop:{' '}
            {formatQuantity(line.purchase.quantity, line.purchase.unit)}
          </span>
        )}
        {short && !line.purchase && <span className="ro-purchase-hint">Not sold in the shop — add stock to your inventory another way.</span>}
      </div>
    </li>
  )
}

/**
 * Step 2: the ingredient check. Shows what the recipe needs against what the kitchen holds (a
 * read-only check, re-run on demand), lets the user buy what's missing in the shop, and reserves
 * the ingredients — which is what unlocks payment.
 */
export default function IngredientsStep({ order, userId, onOrderChange, onNavigate, onError }: OrderStepProps) {
  const navigate = useNavigate()
  const [report, setReport] = useState<AvailabilityReport | null>(null)
  const [checkError, setCheckError] = useState<string | null>(null)
  const [checking, setChecking] = useState(true)
  const [checkKey, setCheckKey] = useState(0)
  const [reserving, setReserving] = useState(false)

  // Re-checked whenever the order itself changes too (e.g. re-fetched after a conflict).
  const orderVersion = order.timestamps?.updatedAt ?? order.status
  useEffect(() => {
    let cancelled = false
    RecipeOrderApi.availability(order.id)
      .then((result) => {
        if (cancelled) return
        setReport(result)
        setCheckError(null)
      })
      .catch((error: unknown) => {
        if (!cancelled) setCheckError(error instanceof Error ? error.message : 'Unable to check ingredient availability')
      })
      .finally(() => {
        if (!cancelled) setChecking(false)
      })
    return () => {
      cancelled = true
    }
  }, [order.id, orderVersion, checkKey])

  const recheck = () => {
    setChecking(true)
    setCheckKey((value) => value + 1)
  }

  const shortLines = report?.lines.filter((line) => !line.sufficient) ?? []
  const buyable = shortLines.filter((line) => line.purchase)
  const canReserve = Boolean(report?.canReserve) && order.allowedActions.includes('RESERVE')

  const buyMissing = () => {
    let cart = loadCart(userId)
    for (const line of buyable) {
      const purchase = line.purchase!
      cart = mergeCartLine(cart, {
        itemId: purchase.ingredientId,
        itemType: 'INGREDIENT',
        itemName: purchase.name || line.name,
        quantity: purchase.quantity,
        unit: purchase.unit,
        price: defaultShopPrice('INGREDIENT'),
      }, 'atLeast')
    }
    saveCart(userId, cart)
    navigate(`/kitchen/shop?resumeRecipeOrder=${order.id}`, { state: { recipeOrderCode: order.orderCode } satisfies ShopResumeState })
  }

  const reserve = async () => {
    setReserving(true)
    try {
      const next = await RecipeOrderApi.reserve(order.id)
      onOrderChange(next)
      if (next.status === 'AWAITING_PAYMENT') onNavigate('payment')
      else recheck()
    } catch (error) {
      onError(error)
      // Stock moved since the check: show the up-to-date picture.
      if (httpStatusOf(error) === 409) recheck()
    } finally {
      setReserving(false)
    }
  }

  return (
    <div className="ro-stack">
      {order.status === 'INVENTORY_CONFLICT' && (
        <div className="ro-alert ro-alert-warning" role="status">
          Your stock changed while the ingredients were being reserved, so nothing was held. Re-check and try again.
        </div>
      )}
      {order.status === 'RESERVING' && (
        <div className="ro-alert ro-alert-info" role="status">
          The ingredients are being reserved… Re-check in a moment.
        </div>
      )}

      <section className="ro-card">
        <div className="ro-card-header">
          <div>
            <h3 className="ro-card-title">Ingredients for {order.recipe?.name ?? 'this recipe'}</h3>
            <p className="ro-hint">Compared with what your kitchen inventory has free right now. Nothing is held until you continue.</p>
          </div>
          <button type="button" className="ro-btn ro-btn-ghost" onClick={recheck} disabled={checking}>
            {checking ? 'Checking…' : '↻ Re-check'}
          </button>
        </div>

        {checkError && !report ? (
          <div className="ro-alert ro-alert-danger" role="alert">
            {checkError}{' '}
            <button type="button" className="ro-link-btn" onClick={recheck}>Try again</button>
          </div>
        ) : !report ? (
          <div className="ro-empty" role="status">Checking your inventory…</div>
        ) : (
          <>
            {checkError && <div className="ro-alert ro-alert-danger" role="alert">{checkError}</div>}

            {report.issues.length > 0 && (
              <div className="ro-alert ro-alert-danger" role="alert">
                <strong>Some recipe ingredients can't be checked, so this order can't be reserved:</strong>
                <ul className="ro-issue-list">
                  {report.issues.map((issue) => (
                    <li key={`${issue.nodeId}-${issue.ingredientRef}`}>
                      <strong>{issue.name || issue.ingredientRef}</strong>
                      {issue.processName && <> in “{issue.processName}”</>}: {issue.message}
                    </li>
                  ))}
                </ul>
                <p>
                  Fix these steps in the <Link to={recipeToolPath(order.recipeId, 'editor')}>Recipe Editor</Link> (e.g. give the ingredient a
                  quantity and unit), then start a new order for the recipe.
                </p>
              </div>
            )}

            {report.lines.length === 0 ? (
              <div className="ro-empty">This recipe doesn't use any catalog ingredients.</div>
            ) : (
              <ul className={`ro-avail-list${checking ? ' ro-is-busy' : ''}`} aria-busy={checking}>
                {report.lines.map((line) => <AvailabilityRow key={line.ingredientId} line={line} />)}
              </ul>
            )}

            {shortLines.length > 0 && (
              <div className="ro-shortage">
                <div>
                  <strong>{shortLines.length} {shortLines.length === 1 ? 'ingredient is' : 'ingredients are'} short.</strong>{' '}
                  {buyable.length > 0
                    ? 'Add them to your cart, check out in the shop, and you\'ll come straight back here.'
                    : 'Add stock to your inventory, then re-check.'}
                </div>
                {buyable.length > 0 && (
                  <button type="button" className="ro-btn ro-btn-secondary" onClick={buyMissing}>
                    🛒 Buy missing ingredients
                  </button>
                )}
              </div>
            )}
          </>
        )}
      </section>

      <section className="ro-card ro-confirm-bar">
        <div className="ro-actions">
          {order.allowedActions.includes('EDIT') && (
            <button type="button" className="ro-btn ro-btn-ghost" onClick={() => onNavigate('confirm')}>
              ← Order details
            </button>
          )}
          <CancelOrderButton order={order} onOrderChange={onOrderChange} onError={onError} />
          <button type="button" className="ro-btn ro-btn-primary" onClick={() => void reserve()} disabled={!canReserve || checking || reserving}>
            {reserving ? 'Reserving ingredients…' : 'Continue to payment'}
          </button>
        </div>
        {report && !report.canReserve && (
          <p className="ro-hint ro-actions-hint">
            {report.issues.length > 0 ? 'Resolve the ingredient problems above to continue.' : 'Continue once every ingredient is in stock.'}
          </p>
        )}
      </section>
    </div>
  )
}
