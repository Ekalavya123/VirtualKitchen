import { useMemo, useState } from 'react'
import { DeliveryAddressApi, RecipeOrderApi, httpStatusOf } from '../../../api'
import type { DeliveryAddress } from '../../../types/recipeOrder'
import { formatTotalMinutes } from '../../recipe-tool/presentation/model/recipePresentation'
import CancelOrderButton from '../components/CancelOrderButton'
import DeliveryAddressForm from '../components/DeliveryAddressForm'
import OrderThumb from '../components/OrderThumb'
import ProcessPreview from '../components/ProcessPreview'
import ServingsStepper from '../components/ServingsStepper'
import { cleanAddress, missingAddressFields, sameAddress } from '../model/orderFormat'
import { buildOrderPresentation } from '../model/orderPresentation'
import type { OrderStepProps } from './stepProps'

const errorMessage = (error: unknown, fallback: string) => (error instanceof Error ? error.message : fallback)

/**
 * Step 1: check the recipe being ordered (its snapshot) and the order details — servings (saved on
 * every change), delivery address and notes — then explicitly confirm. Confirming saves any changed
 * details and asks the server to work out the ingredient requirements.
 */
export default function ConfirmStep({ order, onOrderChange, onNavigate, onError }: OrderStepProps) {
  const recipe = order.recipe
  const presentation = useMemo(() => buildOrderPresentation(recipe?.processes), [recipe?.processes])
  const editable = order.allowedActions.includes('EDIT')

  const [address, setAddress] = useState<DeliveryAddress>(() => order.deliveryAddress ?? {})
  const [saveAsDefault, setSaveAsDefault] = useState(false)
  const [notes, setNotes] = useState(order.notes ?? '')
  const [acknowledged, setAcknowledged] = useState(false)
  const [savingServings, setSavingServings] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [attempted, setAttempted] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  const missing = missingAddressFields(address)

  const changeServings = async (servings: number) => {
    if (servings === order.servings || savingServings) return
    setSavingServings(true)
    try {
      onOrderChange(await RecipeOrderApi.update(order.id, { servings }))
    } catch (error) {
      onError(error)
    } finally {
      setSavingServings(false)
    }
  }

  const submit = async () => {
    setAttempted(true)
    setFormError(null)
    if (!acknowledged) return
    if (missing.length > 0) {
      setFormError('Please complete the delivery address before confirming.')
      return
    }

    setSubmitting(true)
    try {
      let current = order
      const nextAddress = cleanAddress(address)
      const nextNotes = notes.trim()
      if (!sameAddress(nextAddress, order.deliveryAddress) || nextNotes !== (order.notes ?? '').trim()) {
        current = await RecipeOrderApi.update(order.id, { deliveryAddress: nextAddress, notes: nextNotes })
        onOrderChange(current)
      }

      if (current.allowedActions.includes('CONFIRM')) {
        current = await RecipeOrderApi.confirm(order.id, saveAsDefault)
        onOrderChange(current)
      } else if (saveAsDefault) {
        // Already confirmed (only the address/notes changed): save the default address on its own.
        await DeliveryAddressApi.save(nextAddress)
      }
      onNavigate('ingredients')
    } catch (error) {
      const status = httpStatusOf(error)
      // Validation problems belong next to the form; anything else (e.g. a conflict) is a toast + refetch.
      if (status === 400 || status === 422) setFormError(errorMessage(error, 'Please check the order details.'))
      else onError(error)
    } finally {
      setSubmitting(false)
    }
  }

  const alreadyConfirmed = !order.allowedActions.includes('CONFIRM')

  return (
    <div className="ro-stack">
      <section className="ro-card ro-recipe-card">
        <OrderThumb src={recipe?.thumbnailUrl} alt={recipe?.name ?? 'Recipe'} icon={recipe?.fallbackIcon} size="lg" />
        <div className="ro-recipe-text">
          <div className="ro-eyebrow">You're ordering</div>
          <h2 className="ro-title">{recipe?.name ?? 'Recipe'}</h2>
          {recipe?.description && <p className="ro-muted">{recipe.description}</p>}
          <ul className="ro-chips">
            <li className="ro-chip">🧾 {recipe?.stepCount ?? order.totalSteps} {(recipe?.stepCount ?? order.totalSteps) === 1 ? 'step' : 'steps'}</li>
            {presentation.totalMinutes != null && <li className="ro-chip">⏱ {formatTotalMinutes(presentation.totalMinutes)} of timed steps</li>}
            <li className="ro-chip">🏠 {order.kitchenName}</li>
            {recipe?.ownerName && <li className="ro-chip">👩‍🍳 Recipe by {recipe.ownerName}</li>}
          </ul>
        </div>
      </section>

      <div className="ro-columns">
        <div className="ro-stack">
          <section className="ro-card">
            <h3 className="ro-card-title">{order.baseServings == null ? 'Batches' : 'Servings'}</h3>
            <ServingsStepper
              value={order.servings}
              baseServings={order.baseServings}
              onChange={(value) => void changeServings(value)}
              saving={savingServings}
              disabled={!editable || submitting}
            />
            {alreadyConfirmed && editable && (
              <p className="ro-hint">Changing the servings sends the order back for confirmation, so its ingredients are worked out again.</p>
            )}
          </section>

          <section className="ro-card">
            <h3 className="ro-card-title">Delivery address</h3>
            <DeliveryAddressForm value={address} onChange={setAddress} missing={attempted ? missing : []} disabled={!editable || submitting} />
            <label className="ro-check">
              <input type="checkbox" checked={saveAsDefault} onChange={(event) => setSaveAsDefault(event.target.checked)} disabled={submitting} />
              Save as my default delivery address
            </label>
          </section>

          <section className="ro-card">
            <h3 className="ro-card-title">
              <label htmlFor="ro-notes">Notes for the kitchen</label>
            </h3>
            <textarea
              id="ro-notes"
              className="ro-input ro-textarea"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
              placeholder="Optional — e.g. less spicy, no garnish"
              maxLength={1000}
              disabled={!editable || submitting}
            />
          </section>
        </div>

        <section className="ro-card">
          <h3 className="ro-card-title">Recipe steps</h3>
          <p className="ro-hint">The order uses this snapshot of your recipe; later edits to the recipe don't change it.</p>
          <ProcessPreview presentation={presentation} />
        </section>
      </div>

      <section className="ro-card ro-confirm-bar">
        <label className="ro-check ro-check-strong">
          <input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} />
          I confirm this recipe and order details
        </label>
        {formError && (
          <div className="ro-alert ro-alert-danger" role="alert">
            {formError}
          </div>
        )}
        <div className="ro-actions">
          <CancelOrderButton order={order} onOrderChange={onOrderChange} onError={onError} />
          <button
            type="button"
            className="ro-btn ro-btn-primary"
            onClick={() => void submit()}
            disabled={!acknowledged || submitting || savingServings}
          >
            {submitting ? 'Confirming…' : alreadyConfirmed ? 'Save & check ingredients' : 'Confirm & check ingredients'}
          </button>
        </div>
      </section>
    </div>
  )
}
