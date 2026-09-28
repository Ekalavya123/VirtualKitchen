import type {
  CheckOutcome,
  RecipeBranchTag,
  RecipeTimelineCheck,
  RecipeTimelineSection,
  RecipeTimelineStep,
  StepContinuation,
} from '../model/recipePresentation'
import RecipeImage from './RecipeImage'
import { scrollToAnchor, stepAnchorId } from './scrollToAnchor'

type RecipeStepsTimelineProps = {
  sections: RecipeTimelineSection[]
}

/**
 * The recipe's steps as a numbered vertical timeline. A recipe with subprocesses reads as parts
 * ("Part 1 · Marinate the chicken", …, then the main process), each prepared before the part
 * that uses it; a recipe with a single process reads as one plain list of steps.
 */
export default function RecipeStepsTimeline({ sections }: RecipeStepsTimelineProps) {
  const showParts = sections.length > 1

  return (
    <div>
      {sections.map((section, index) => (
        <section key={section.key} id={section.anchorId} className="rp-part" aria-label={section.title}>
          {showParts && (
            <div className="rp-part-header">
              <span className="rp-part-label">{section.isMain ? 'Finally' : `Part ${index + 1}`}</span>
              <h3 className="rp-part-title">{section.title}</h3>
              <span className="rp-part-rule" aria-hidden />
            </div>
          )}
          <ol className="rp-timeline">
            {section.items.map((item) =>
              item.kind === 'step' ? <StepItem key={item.key} step={item} /> : <CheckItem key={item.key} check={item} />)}
          </ol>
        </section>
      ))}
    </div>
  )
}

function BranchTag({ branch }: { branch?: RecipeBranchTag }) {
  if (!branch) return null
  return (
    <div className="rp-branch-tag">
      <span aria-hidden>🔀</span> Only if “{branch.answer}”{branch.question && <span>· {branch.question}</span>}
    </div>
  )
}

function StepItem({ step }: { step: RecipeTimelineStep }) {
  const hasImage = Boolean(step.imageUrl)

  return (
    <li id={stepAnchorId(step.number)} className="rp-timeline-item">
      <span className="rp-step-number" aria-label={`Step ${step.number}`}>{step.number}</span>

      <article className={`rp-card rp-step-card${hasImage ? ' has-image' : ''}`}>
        {hasImage && (
          <div className="rp-step-media">
            <RecipeImage src={step.imageUrl} alt={step.title} fallbackIcon={step.icon} />
          </div>
        )}

        <div className="rp-step-body">
          <BranchTag branch={step.branch} />

          <div className="rp-step-heading">
            {!hasImage && <span className="rp-step-icon" aria-hidden>{step.icon}</span>}
            <h4 className="rp-step-title">{step.title}</h4>
          </div>

          {step.description && <p className="rp-step-description">{step.description}</p>}

          {step.ingredients.length > 0 && (
            <div className="rp-chip-row" aria-label="Ingredients for this step">
              {step.ingredients.map((ingredient) => (
                <span key={ingredient.key} className="rp-chip">
                  <span aria-hidden>{ingredient.icon}</span>
                  {ingredient.name}
                  {ingredient.amount && <span className="rp-chip-amount">{ingredient.amount}</span>}
                  {ingredient.preparation && <span className="rp-chip-muted">· {ingredient.preparation}</span>}
                </span>
              ))}
            </div>
          )}

          {step.uses.length > 0 && (
            <div className="rp-chip-row" aria-label="Uses">
              {step.uses.map((use) =>
                use.anchorId ? (
                  <button
                    key={use.key}
                    type="button"
                    className="rp-chip rp-chip-link"
                    onClick={() => scrollToAnchor(use.anchorId as string)}
                  >
                    <span aria-hidden>🥣</span>
                    {use.label}
                    <span className="rp-chip-muted">· {use.source} ↑</span>
                  </button>
                ) : (
                  <span key={use.key} className="rp-chip">
                    <span aria-hidden>🥣</span>
                    {use.label}
                    <span className="rp-chip-muted">· {use.source}</span>
                  </span>
                ))}
            </div>
          )}

          {step.details.length > 0 && (
            <div className="rp-chip-row" aria-label="Cooking details">
              {step.details.map((detail) => (
                <span key={detail.kind} className="rp-chip rp-chip-detail" title={detail.hint}>
                  <span aria-hidden>{detail.icon}</span>
                  {detail.label}
                </span>
              ))}
            </div>
          )}

          {step.expectedOutput && (
            <div className="rp-look-for">
              <span aria-hidden>👀</span>
              <span><strong>Look for:</strong> {step.expectedOutput}</span>
            </div>
          )}

          <Continuation continuation={step.continuation} />
        </div>
      </article>
    </li>
  )
}

function Continuation({ continuation }: { continuation?: StepContinuation }) {
  if (!continuation) return null

  if (continuation.kind === 'end') {
    return <span className="rp-continuation">🏁 This path ends here</span>
  }

  if (continuation.kind === 'choice') {
    const numbers = continuation.targetNumbers
    const list = numbers.length > 1 ? `${numbers.slice(0, -1).join(', ')} and ${numbers[numbers.length - 1]}` : String(numbers[0])
    return <span className="rp-continuation">→ Next, continue with steps {list}</span>
  }

  return (
    <button type="button" className="rp-continuation" onClick={() => scrollToAnchor(stepAnchorId(continuation.targetNumber))}>
      {continuation.direction === 'back' ? '↻ Then go back to' : '→ Then skip ahead to'} step {continuation.targetNumber}
      {continuation.targetTitle && ` · ${continuation.targetTitle}`}
    </button>
  )
}

const outcomeIcon = (outcome: CheckOutcome) => {
  if (outcome.direction === 'back') return '↻'
  return outcome.tone === 'yes' ? '✓' : '✕'
}

const outcomeAction = (outcome: CheckOutcome) => {
  const target = `step ${outcome.targetNumber}${outcome.targetTitle ? ` — ${outcome.targetTitle}` : ''}`
  if (outcome.direction === 'back') return `Go back to ${target}`
  if (outcome.direction === 'forward') return `Skip ahead to ${target}`
  return `Carry on with ${target}`
}

function CheckItem({ check }: { check: RecipeTimelineCheck }) {
  return (
    <li id={stepAnchorId(check.number)} className="rp-timeline-item is-check">
      <span className="rp-step-number" aria-label={`Step ${check.number}`}>{check.number}</span>

      <article className="rp-card rp-check-card">
        <BranchTag branch={check.branch} />
        <span className="rp-check-eyebrow"><span aria-hidden>🔍</span> Check</span>
        <h4 className="rp-check-question">{check.question}</h4>
        {check.notes && <p className="rp-check-notes">{check.notes}</p>}

        {check.outcomes.length > 0 && (
          <div className="rp-outcomes">
            {check.outcomes.map((outcome) => (
              <button
                key={outcome.key}
                type="button"
                className={`rp-outcome is-${outcome.tone}`}
                onClick={() => scrollToAnchor(stepAnchorId(outcome.targetNumber))}
              >
                <span className="rp-outcome-icon" aria-hidden>{outcomeIcon(outcome)}</span>
                <span className="rp-outcome-text">
                  <span className="rp-outcome-answer">{outcome.answer}</span>
                  <span className="rp-outcome-action">{outcomeAction(outcome)}</span>
                </span>
              </button>
            ))}
          </div>
        )}
      </article>
    </li>
  )
}
