import './HomePage.css'
import chefLogo from '../assets/kitchen/blackShadowChef.png'
import { isAuthenticated } from '../shared/auth/session'

type Props = {
  onTryIt?: () => void
  onLogin?: () => void
}

export default function HomePage({ onTryIt, onLogin }: Props) {
  const hideLogin = isAuthenticated()
  return (
    <div className="home-page">
      <header className="home-nav">
        <div className="brand">
          <span className="logo">
            <img src={chefLogo} alt="Virtual Kitchen Logo" className="logo-icon" />
          </span> 
          Virtual Kitchen
        </div>
        <nav>
          <a href="#how-it-works">How it works</a>
          <a href="#features">Features</a>
          <a href="#vision">Our vision</a>
        </nav>
        <div className="nav-actions">
          {!hideLogin && (
            <button className="login-btn" onClick={onLogin}>Log in</button>
          )}
          <button className="primary-btn" onClick={onTryIt}>Try it</button>
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="hero-copy">
            <span className="eyebrow">YOUR RECIPE. YOUR WAY.</span>
            <h1>Cooking without <em>the abstraction.</em></h1>
            <p>
              Build your recipe exactly how you want it. Customize ingredients,
              quantities and cooking steps, then see the process come to life.
            </p>
            <div className="actions">
              <button className="primary-btn big" onClick={onTryIt}>Create your recipe →</button>
              <a href="#how-it-works">See how it works</a>
            </div>
            <div className="points">
              <span>✓ You decide the recipe</span>
              <span>✓ AI assists, not controls</span>
              <span>✓ Visualize the process</span>
            </div>
          </div>

          {/* REPLACE THIS MOCK WITH YOUR REAL FLOW → VIDEO VISUAL */}
          <div className="hero-visual">
            <small>RECIPE → VISUALIZATION</small>
            <FlowCard icon="🔪" title="Cut onions" detail="2 medium onions" />
            <b className="arrow">↓</b>
            <FlowCard icon="🔥" title="Heat oil" detail="Medium flame · 2 min" />
            <b className="arrow">↓</b>
            <FlowCard icon="🍚" title="Add rice" detail="250 g · cooked rice" />
            <b className="arrow">↓</b>
            <div className="dish">🍽️</div>
            <p className="caption">Your recipe flow becomes a cooking experience.</p>
          </div>
        </section>

        <section className="section white" id="how-it-works">
          <SectionHeading eyebrow="THE IDEA" title="Cooking shouldn't be a black box.">
            Most recipes tell you what to do. Virtual Kitchen is built around
            showing how the complete process happens.
          </SectionHeading>
          <div className="three-grid">
            <Feature number="01" icon="✦" title="Create">Build your recipe as a flow. Choose ingredients, quantities and cooking operations.</Feature>
            <Feature number="02" icon="◎" title="Visualize">Turn the recipe flow into a visual representation of the cooking process.</Feature>
            <Feature number="03" icon="♨" title="Cook">Chef: Follow the process you designed instead of relying only on instructions.</Feature>
          </div>
        </section>

        <section className="section dark" id="features">
          <SectionHeading eyebrow="SEE IT COME TO LIFE" title="From an idea to a cooking process.">
            This is the place for your real product animation.
          </SectionHeading>
          {/* REPLACE THIS ENTIRE BLOCK WITH YOUR 10–15 SECOND CLIP */}
          <div className="pipeline">
            <Stage n="01" title="Recipe text" text='"Make spicy egg fried rice..."' />
            <span>→</span>
            <Stage n="02" title="AI understands" text="Structured cooking steps" />
            <span>→</span>
            <Stage n="03" title="Recipe flow" text="Nodes + connections" />
            <span>→</span>
            <div className="video-placeholder"><div>▶</div><b>Your cooking clip</b><small>Replace with MP4</small></div>
          </div>
        </section>

        <section className="section beige chef">
          <div>
            <span className="eyebrow">YOU ARE THE CHEF</span>
            <h2>AI can suggest. You decide.</h2>
            <p>Define the recipe. AI helps you understand it, improve it and visualize it.</p>
            <div className="chef-line"><strong>YOU</strong><i /><span>AI ASSISTANT</span></div>
          </div>
          <div className="chat">
            <div><small>You</small><p>Can I use less oil?</p></div>
            <div className="ai"><small>AI suggestion</small><p>Yes. Reduce the oil and increase stirring slightly to maintain texture.</p></div>
            <small className="muted">You choose whether to apply it.</small>
          </div>
        </section>

        <section className="section white">
          <SectionHeading eyebrow="YOUR FOOD. YOUR RULES." title="Cook however you want." />
          <div className="four-grid">
            <Rule icon="🥕" title="Ingredients">Choose what goes into your recipe.</Rule>
            <Rule icon="⚖️" title="Quantity">Set the amount that works for you.</Rule>
            <Rule icon="🔥" title="Process">Design the cooking process your way.</Rule>
            <Rule icon="🧠" title="AI help">Get suggestions when you need them.</Rule>
          </div>
        </section>

        <section className="section beige" id="vision">
          <SectionHeading eyebrow="OUR VISION" title="From virtual kitchen to real cooking.">
            The long-term idea goes beyond visualization: design, customize,
            visualize, order and eventually have the recipe prepared for you.
          </SectionHeading>
          <div className="vision">
            {['⌘ Design','✎ Customize','◉ Visualize','🛒 Order','♨ Cook'].map((x, i) => (
              <span key={x}>{x}{i < 4 && ' →'}</span>
            ))}
          </div>
        </section>

        <section className="cta">
          <span className="eyebrow">READY?</span>
          <h2>Cook your way.</h2>
          <p>Create your first recipe and see where it takes you.</p>
          <button className="primary-btn big light" onClick={onTryIt}>Try Virtual Kitchen →</button>
        </section>
      </main>

      <footer><span>© 2026 Virtual Kitchen</span><div><a href="#privacy">Privacy</a><a href="#terms">Terms</a><a href="#contact">Contact</a></div></footer>
    </div>
  )
}

function FlowCard({ icon, title, detail }: { icon: string; title: string; detail: string }) {
  return <div className="flow-card"><span>{icon}</span><div><b>{title}</b><small>{detail}</small></div></div>
}
function SectionHeading({ eyebrow, title, children }: { eyebrow: string; title: string; children?: React.ReactNode }) {
  return <div className="heading"><span className="eyebrow">{eyebrow}</span><h2>{title}</h2>{children && <p>{children}</p>}</div>
}
function Feature({ number, icon, title, children }: { number: string; icon: string; title: string; children: string }) {
  return <article className="feature"><div><span>{icon}</span><small>{number}</small></div><h3>{title}</h3><p>{children}</p></article>
}
function Stage({ n, title, text }: { n: string; title: string; text: string }) {
  return <div className="stage"><small>{n}</small><b>{title}</b><span>{text}</span></div>
}
function Rule({ icon, title, children }: { icon: string; title: string; children: string }) {
  return <article className="rule"><span>{icon}</span><h3>{title}</h3><p>{children}</p></article>
}
