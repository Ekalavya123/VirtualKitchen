import { useRef } from 'react'
import { useInView, useTypewriter } from './useReveal'

const PROMPT = 'Make a spicy egg fried rice for two, less oil please.'

/** Chat mock for the AI section: the prompt types out, then the AI proposal waits for approval. */
export default function AiDemo() {
  const ref = useRef<HTMLDivElement>(null)
  const inView = useInView(ref)
  const { typed, done } = useTypewriter(PROMPT, inView)

  return (
    <div ref={ref} className="home-ai-panel">
      <div className="home-ai-msg is-user">
        <small>You</small>
        <p>
          <span className="home-sr-only">{PROMPT}</span>
          <span aria-hidden="true">{typed}{!done && <span className="home-caret" />}</span>
        </p>
      </div>

      <div className={`home-ai-msg is-ai${done ? ' is-shown' : ''}`}>
        <small>✦ AI proposal</small>
        <p>Here’s a plan with <b>7 steps</b>, <b>1 condition</b> and <b>6 ingredients</b>. Shall I build the flow?</p>
        <ul className="home-ai-plan">
          <li>Prep vegetables and whisk eggs</li>
          <li>Stir-fry aromatics in 1 tbsp oil</li>
          <li>Toss rice, season, garnish</li>
        </ul>
        <div className="home-ai-actions" aria-hidden="true">
          <span className="home-mini-btn is-primary">Approve &amp; build</span>
          <span className="home-mini-btn">Edit plan</span>
        </div>
      </div>

      <small className="home-ai-note">Nothing changes in your recipe until you approve it.</small>
    </div>
  )
}
