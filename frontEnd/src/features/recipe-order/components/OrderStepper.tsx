import { Link } from 'react-router-dom'
import type { RecipeOrderStatus } from '../../../types/recipeOrder'
import { buildOrderStepper, recipeOrderPath, type RecipeOrderView } from '../model/orderView'

type OrderStepperProps = {
  orderId: number
  status: RecipeOrderStatus
  view: RecipeOrderView
}

/** Confirm -> Ingredients -> Payment -> Receipt -> Tracking; steps the status allows are links. */
export default function OrderStepper({ orderId, status, view }: OrderStepperProps) {
  const steps = buildOrderStepper(status, view)

  return (
    <nav className="ro-stepper" aria-label="Order progress">
      <ol>
        {steps.map((step, index) => {
          const content = (
            <>
              <span className="ro-step-dot" aria-hidden>{step.state === 'done' ? '✓' : index + 1}</span>
              <span className="ro-step-label">{step.label}</span>
            </>
          )
          return (
            <li key={step.view} className={`ro-step ro-step-${step.state}`}>
              {step.navigable ? (
                <Link to={recipeOrderPath(orderId, step.view)} className="ro-step-inner">
                  {content}
                </Link>
              ) : (
                <span className="ro-step-inner" aria-current={step.state === 'current' ? 'step' : undefined}>
                  {content}
                </span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
