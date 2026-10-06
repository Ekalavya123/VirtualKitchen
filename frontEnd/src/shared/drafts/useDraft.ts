import { useCallback, useState } from 'react'
import { clearDraft, readDraft, writeDraft } from './draftStore'

const sameValue = (a: unknown, b: unknown) => {
  try {
    return JSON.stringify(a) === JSON.stringify(b)
  } catch {
    return false
  }
}

/**
 * `useState` for form input that must survive closing the form, navigating away and reloading,
 * until the form's action succeeds. Starts from the saved draft for `key` (else `initial`), saves
 * every change, and `clear()` — call it once the action succeeded, or when the user discards the
 * draft — resets to `initial` and forgets it. `restored` tells the form its content came from an
 * earlier visit. Emptying the form back to `initial` forgets the draft too.
 *
 * A null `key` makes it plain state (nothing saved), for forms without a stable identity yet.
 */
export function useDraft<T>(key: string | null, initial: T) {
  // The first `initial` is the form's empty state for its whole life (callers pass fresh literals).
  const [initialValue] = useState(initial)
  const [state, setState] = useState(() => {
    const saved = key ? readDraft<T>(key) : undefined
    return { key, value: saved ?? initialValue, restored: saved !== undefined }
  })

  // A different key (e.g. another step selected) is a different draft: switch while rendering.
  let current = state
  if (state.key !== key) {
    const saved = key ? readDraft<T>(key) : undefined
    current = { key, value: saved ?? initialValue, restored: saved !== undefined }
    setState(current)
  }

  const setValue = useCallback((next: T | ((previous: T) => T)) => {
    setState((previous) => {
      const value = typeof next === 'function' ? (next as (previous: T) => T)(previous.value) : next
      // Idempotent, so React running this updater twice (StrictMode) is harmless.
      if (previous.key) {
        if (sameValue(value, initialValue)) clearDraft(previous.key)
        else writeDraft(previous.key, value)
      }
      return { ...previous, value }
    })
  }, [initialValue])

  const clear = useCallback(() => {
    setState((previous) => {
      if (previous.key) clearDraft(previous.key)
      return { ...previous, value: initialValue, restored: false }
    })
  }, [initialValue])

  return { value: current.value, setValue, clear, restored: current.restored }
}
