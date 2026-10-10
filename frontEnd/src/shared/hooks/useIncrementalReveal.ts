import { useCallback, useEffect, useState } from 'react'

const DEFAULT_PAGE_SIZE = 12

/**
 * Shows a long list a page at a time: the first `pageSize` items, then the next page each time
 * the sentinel element (rendered after the visible items) scrolls into view. `resetKey` — e.g.
 * the active tab, search text and filters — starts again from the first page when it changes.
 */
export function useIncrementalReveal<T>(items: readonly T[], resetKey: string, pageSize = DEFAULT_PAGE_SIZE) {
  const [state, setState] = useState({ key: resetKey, count: pageSize })
  // A new list (other tab, search or filter) starts from the first page again.
  const count = state.key === resetKey ? state.count : pageSize
  if (state.key !== resetKey) setState({ key: resetKey, count: pageSize })

  const hasMore = count < items.length
  const visible = hasMore ? items.slice(0, count) : items

  const showMore = useCallback(
    () => setState(current => ({ key: resetKey, count: (current.key === resetKey ? current.count : pageSize) + pageSize })),
    [resetKey, pageSize],
  )

  // The sentinel is kept in state (a callback ref), so the observer below follows it.
  const [sentinel, setSentinel] = useState<HTMLElement | null>(null)
  const sentinelRef = useCallback((node: HTMLElement | null) => setSentinel(node), [])

  useEffect(() => {
    if (!sentinel || !hasMore) return
    // Re-created after every page: observe() reports the sentinel's current state, so if it is
    // still in view once the new page has rendered, the next page loads straight away.
    const observer = new IntersectionObserver(
      entries => {
        if (entries.some(entry => entry.isIntersecting)) showMore()
      },
      // Start loading a little before the end comes into view.
      { rootMargin: '0px 0px 320px 0px' },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [sentinel, hasMore, count, showMore])

  return { visible, hasMore, total: items.length, sentinelRef, showMore }
}
