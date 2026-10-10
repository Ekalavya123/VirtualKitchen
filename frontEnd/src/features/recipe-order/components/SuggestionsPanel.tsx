/** Placeholder chat with the chef — informational only: no messages are sent or stored. */
export default function SuggestionsPanel() {
  return (
    <div className="ro-stack">
      <div className="ro-empty ro-suggestions-empty">
        <div className="ro-empty-icon" aria-hidden>💬</div>
        <p>
          This is where you will be able to send suggestions or additional instructions to the chef while your recipe is being
          prepared. In this prototype, messaging is for demonstration only.
        </p>
      </div>

      <div className="ro-chat" aria-label="Sample messages">
        <div className="ro-chat-msg ro-chat-mine">
          <span className="ro-badge ro-badge-neutral">Demo content</span>
          <p>Could you go a little lighter on the chilli, please?</p>
        </div>
        <div className="ro-chat-msg ro-chat-theirs">
          <span className="ro-badge ro-badge-neutral">Demo content</span>
          <p>Sure — I'll halve it and keep some on the side.</p>
        </div>
      </div>

      <form className="ro-chat-form" onSubmit={(event) => event.preventDefault()}>
        <label htmlFor="ro-suggestion" className="ro-visually-hidden">Message to the chef</label>
        <input id="ro-suggestion" className="ro-input" placeholder="Messaging isn't available in this prototype" disabled />
        <button type="submit" className="ro-btn ro-btn-primary" disabled>
          Send
        </button>
      </form>
    </div>
  )
}
