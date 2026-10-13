import { useCallback, useEffect, useState } from 'react'
import { errorMessage } from '../../../../shared/errors'

interface State<T> {
  key: string | null
  data: T | null
  error: string | null
}

/**
 * Loads data for a request key: whenever {@code key} changes the request runs again, stale responses are
 * dropped, and {@code loading} is derived (true until the response for the current key arrives).
 * {@code reload} re-runs the current request.
 */
export function useApiData<T>(key: string | null, load: () => Promise<T>) {
  const [state, setState] = useState<State<T>>({ key: null, data: null, error: null })
  const [reloadCount, setReloadCount] = useState(0)
  const requestKey = key === null ? null : `${key}#${reloadCount}`

  useEffect(() => {
    if (requestKey === null) return undefined
    let cancelled = false
    load()
      .then((data) => {
        if (!cancelled) setState({ key: requestKey, data, error: null })
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState((previous) => ({
            key: requestKey,
            data: previous.data,
            error: errorMessage(error, 'Request failed'),
          }))
        }
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- requestKey captures everything load depends on
  }, [requestKey])

  const reload = useCallback(() => setReloadCount((count) => count + 1), [])

  return {
    data: state.data,
    error: state.key === requestKey ? state.error : null,
    loading: requestKey !== null && state.key !== requestKey,
    reload,
  }
}
