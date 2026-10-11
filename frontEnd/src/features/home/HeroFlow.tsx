import type { CSSProperties } from 'react'

const order = (i: number) => ({ '--i': i }) as CSSProperties

/** Static mock of the Recipe Tool editor: nodes pop in and edges draw on load (CSS-driven). */
export default function HeroFlow() {
  return (
    <div className="home-canvas" role="img" aria-label="Example recipe flow in the Recipe Tool editor">
      <div className="home-canvas-bar">
        <span className="home-canvas-dots" aria-hidden="true"><i /><i /><i /></span>
        <b>Spicy egg fried rice</b>
        <span className="home-chip">Editor</span>
        <span className="home-chip is-magic">✦ AI drafted</span>
      </div>

      <div className="home-canvas-body">
        <FlowNode i={0} icon="🔪" title="Chop onions" detail="2 medium · 1 min" tag="Prep" />
        <span className="home-edge" style={order(1)} />
        <FlowNode i={2} icon="🔥" title="Heat oil" detail="Medium flame · 2 min" tag="Cook" active />
        <span className="home-edge" style={order(3)} />
        <div className="home-node is-condition" style={order(4)}>
          <span className="home-node-diamond" aria-hidden="true">◇</span>
          <b>Onions golden?</b>
          <div className="home-node-branches">
            <span className="is-yes">Yes → continue</span>
            <span className="is-no">No → 1 more min</span>
          </div>
        </div>
        <span className="home-edge" style={order(5)} />
        <FlowNode i={6} icon="🍚" title="Add rice & eggs" detail="250 g rice · 2 eggs" tag="Cook" />
      </div>

      <div className="home-canvas-player" style={order(7)}>
        <span className="home-play" aria-hidden="true">▶</span>
        <div>
          <b>Narrated walkthrough</b>
          <small>Step 2 of 6 · “Heat the oil over a medium flame…”</small>
        </div>
        <span className="home-wave" aria-hidden="true"><i /><i /><i /><i /><i /></span>
      </div>
    </div>
  )
}

function FlowNode({ i, icon, title, detail, tag, active = false }: {
  i: number
  icon: string
  title: string
  detail: string
  tag: string
  active?: boolean
}) {
  return (
    <div className={`home-node${active ? ' is-active' : ''}`} style={order(i)}>
      <span className="home-node-icon" aria-hidden="true">{icon}</span>
      <div className="home-node-text">
        <b>{title}</b>
        <small>{detail}</small>
      </div>
      <span className="home-node-tag">{tag}</span>
    </div>
  )
}
