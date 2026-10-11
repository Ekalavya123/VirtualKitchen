import { useEffect, useRef, useState } from 'react'
import './PaymentGatewayOverlay.css'

export type PaymentPhase = 'processing' | 'success' | 'failure'

type PaymentGatewayOverlayProps = {
  /** Null hides the overlay. */
  phase: PaymentPhase | null
  /** Already formatted, e.g. "₹1,250.00". */
  amount: string
  /** What is being paid for, e.g. "Shop order" or the recipe name. */
  description: string
  /** Shown on success, e.g. the order code or the demo payment reference. */
  reference?: string | null
  /** Shown on failure. */
  failureMessage?: string | null
  /** Called when the user dismisses a finished payment (or, after success, automatically). */
  onDone: () => void
  successLabel?: string
}

const PROCESSING_STEPS = ['Connecting to the demo gateway', 'Authorising payment', 'Confirming with Virtual Kitchen']
const STEP_MS = 650
const AUTO_CONTINUE_MS = 2600

/**
 * A simulated payment gateway: an animated "processing" sheet while the (demo) payment request
 * runs, then an animated success tick or failure cross. Purely presentational — the outcome is
 * whatever the server answered; no card or account details are ever asked for.
 */
export default function PaymentGatewayOverlay({
  phase,
  amount,
  description,
  reference,
  failureMessage,
  onDone,
  successLabel = 'Continue',
}: PaymentGatewayOverlayProps) {
  if (!phase) return null
  return (
    <div className="pay-overlay" role="dialog" aria-modal="true" aria-labelledby="pay-overlay-title">
      <div className={`pay-sheet pay-sheet-${phase}`}>
        <div className="pay-sheet-brand">
          <span className="pay-brand-logo" aria-hidden="true">VK</span>
          <span>Virtual Kitchen Pay</span>
          <span className="pay-demo-badge">Demo gateway</span>
        </div>

        <div className="pay-sheet-amount">
          <span>{description}</span>
          <strong>{amount}</strong>
        </div>

        {phase === 'processing' ? (
          <Processing />
        ) : (
          <Result
            key={phase}
            success={phase === 'success'}
            reference={reference}
            failureMessage={failureMessage}
            onDone={onDone}
            successLabel={successLabel}
          />
        )}

        <p className="pay-sheet-note">Simulated payment — no money is moved and no card details are collected.</p>
      </div>
    </div>
  )
}

function Processing() {
  const [step, setStep] = useState(0)
  useEffect(() => {
    const timer = window.setInterval(() => setStep(current => Math.min(current + 1, PROCESSING_STEPS.length - 1)), STEP_MS)
    return () => window.clearInterval(timer)
  }, [])

  return (
    <div className="pay-body" aria-live="polite">
      <div className="pay-spinner" aria-hidden="true">
        <span />
      </div>
      <h2 id="pay-overlay-title" className="pay-title">
        Processing payment…
      </h2>
      <ol className="pay-steps">
        {PROCESSING_STEPS.map((label, index) => (
          <li key={label} className={index < step ? 'done' : index === step ? 'current' : ''}>
            <span className="pay-step-dot" aria-hidden="true" />
            {label}
          </li>
        ))}
      </ol>
    </div>
  )
}

function Result({
  success,
  reference,
  failureMessage,
  onDone,
  successLabel,
}: {
  success: boolean
  reference?: string | null
  failureMessage?: string | null
  onDone: () => void
  successLabel: string
}) {
  const buttonRef = useRef<HTMLButtonElement>(null)
  const onDoneRef = useRef(onDone)
  useEffect(() => {
    onDoneRef.current = onDone
  }, [onDone])

  useEffect(() => {
    buttonRef.current?.focus()
    if (!success) return
    // A successful payment moves on by itself after the animation has been seen.
    const timer = window.setTimeout(() => onDoneRef.current(), AUTO_CONTINUE_MS)
    return () => window.clearTimeout(timer)
  }, [success])

  return (
    <div className="pay-body" aria-live="assertive">
      <svg className={`pay-mark ${success ? 'pay-mark-success' : 'pay-mark-failure'}`} viewBox="0 0 52 52" aria-hidden="true">
        <circle className="pay-mark-circle" cx="26" cy="26" r="24" />
        {success ? (
          <path className="pay-mark-path" d="M15 27 l7 7 l15 -16" />
        ) : (
          <path className="pay-mark-path" d="M18 18 L34 34 M34 18 L18 34" />
        )}
      </svg>
      <h2 id="pay-overlay-title" className="pay-title">
        {success ? 'Payment successful' : 'Payment failed'}
      </h2>
      <p className="pay-detail">
        {success
          ? reference
            ? `Reference ${reference}`
            : 'Your payment was approved.'
          : failureMessage || 'The payment was declined.'}
      </p>
      <button ref={buttonRef} type="button" className={`pay-done ${success ? 'success' : 'failure'}`} onClick={onDone}>
        {success ? successLabel : 'Close'}
      </button>
    </div>
  )
}
