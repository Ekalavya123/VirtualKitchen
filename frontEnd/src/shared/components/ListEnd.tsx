import type { Ref } from 'react'
import './ListEnd.css'

type ListEndProps = {
  hasMore: boolean
  shown: number
  total: number
  /** Plural noun for the end message, e.g. "recipes". */
  noun: string
  sentinelRef: Ref<HTMLDivElement>
  onShowMore: () => void
}

/**
 * The bottom of a progressively revealed list: while more remain, a sentinel that loads the next
 * page as it scrolls into view (with a button fallback); once everything is shown, an end marker.
 */
export default function ListEnd({ hasMore, shown, total, noun, sentinelRef, onShowMore }: ListEndProps) {
  if (total === 0) return null
  if (hasMore) {
    return (
      <div ref={sentinelRef} className="list-end list-end-loading" role="status">
        <span className="list-end-spinner" aria-hidden="true" />
        <span>
          Showing {shown} of {total} {noun}
        </span>
        <button type="button" className="list-end-more" onClick={onShowMore}>
          Load more
        </button>
      </div>
    )
  }
  return (
    <div className="list-end list-end-done">
      <span className="list-end-line" aria-hidden="true" />
      <span>
        You&apos;ve reached the end · {total} {noun}
      </span>
      <span className="list-end-line" aria-hidden="true" />
    </div>
  )
}
