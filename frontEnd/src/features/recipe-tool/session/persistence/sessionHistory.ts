/**
 * Recipe-wide, linear undo/redo for the Recipe Tool's working session. Editor history only — it
 * has nothing to do with what is persisted: undoing simply makes the session's current snapshot an
 * older one, which autosave then persists like any other change.
 *
 * Entries are whole-recipe snapshots (every process). The session replaces its process array and
 * process objects instead of mutating them, so a snapshot is just a reference to the array as it
 * was — structurally shared with its neighbours, never deep-cloned.
 *
 * `focusProcessId` remembers which process an operation was made in, so undoing an edit made in
 * another process can bring the editor back to it.
 */

export type HistoryEntry<T> = {
  snapshot: T
  focusProcessId: number | null
}

export type RecordOptions = {
  focusProcessId: number | null
  /**
   * Consecutive records with the same key within `coalesceMs` of each other become one entry —
   * one undo step per typing burst in a field instead of one per keystroke.
   */
  coalesceKey?: string
}

export class SessionHistory<T> {
  private past: HistoryEntry<T>[] = []
  private future: HistoryEntry<T>[] = []
  private lastCoalesceKey: string | null = null
  private lastRecordedAt = 0

  private readonly limit: number
  private readonly coalesceMs: number
  private readonly now: () => number

  constructor(options: { limit?: number; coalesceMs?: number; now?: () => number } = {}) {
    this.limit = options.limit ?? 100
    this.coalesceMs = options.coalesceMs ?? 1000
    this.now = options.now ?? Date.now
  }

  get canUndo() {
    return this.past.length > 0
  }

  get canRedo() {
    return this.future.length > 0
  }

  /**
   * Records `before` — the state just *before* a mutation — as an undo step and clears the redo
   * stack (a new edit after undo starts a new branch). Returns false when it was coalesced into
   * the previous step instead.
   */
  record(before: T, options: RecordOptions): boolean {
    const time = this.now()
    const coalesced = options.coalesceKey != null
      && options.coalesceKey === this.lastCoalesceKey
      && time - this.lastRecordedAt <= this.coalesceMs
      && this.past.length > 0
    this.lastCoalesceKey = options.coalesceKey ?? null
    this.lastRecordedAt = time
    this.future = []
    if (coalesced) return false

    this.past.push({ snapshot: before, focusProcessId: options.focusProcessId })
    if (this.past.length > this.limit) this.past.shift()
    return true
  }

  /** Steps back: returns the entry to restore, and remembers `current` for redo. */
  undo(current: T): HistoryEntry<T> | null {
    const entry = this.past.pop()
    if (!entry) return null
    this.future.push({ snapshot: current, focusProcessId: entry.focusProcessId })
    this.lastCoalesceKey = null
    return entry
  }

  /** Steps forward again: returns the entry to restore, and remembers `current` for undo. */
  redo(current: T): HistoryEntry<T> | null {
    const entry = this.future.pop()
    if (!entry) return null
    this.past.push({ snapshot: current, focusProcessId: entry.focusProcessId })
    this.lastCoalesceKey = null
    return entry
  }

  /** Rewrites every stored snapshot — e.g. to swap temporary process ids for the real ones a save created. */
  map(transform: (snapshot: T) => T, mapFocus: (processId: number) => number = (id) => id) {
    const apply = (entry: HistoryEntry<T>): HistoryEntry<T> => ({
      snapshot: transform(entry.snapshot),
      focusProcessId: entry.focusProcessId == null ? null : mapFocus(entry.focusProcessId),
    })
    this.past = this.past.map(apply)
    this.future = this.future.map(apply)
  }

  clear() {
    this.past = []
    this.future = []
    this.lastCoalesceKey = null
  }
}
