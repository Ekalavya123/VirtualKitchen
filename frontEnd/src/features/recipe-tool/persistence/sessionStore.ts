/**
 * The Recipe session's mutable working data — one instance per RecipeSessionProvider. Deliberately
 * not React state: RecipeProcessCanvas writes to it on every keystroke, and re-rendering every
 * consumer through array identity churn each time would be wasteful; consumers react to the
 * provider's `version` instead. Mutations go through methods (like SaveCoordinator's), so the
 * provider never assigns into a hook value directly.
 */

import type { Process } from '../../../types/process'
import { createPersistedState, type PersistedState } from './snapshotPersistence'

export class SessionStore {
  /** Every process of the recipe (MAIN + SUBPROCESSes), including unsaved edits. Replaced, never mutated in place. */
  processes: Process[] = []
  /** What the backend is known to hold (process ids, revision, last saved fingerprint); updated in place by persistSnapshot. */
  persisted: PersistedState = createPersistedState([], 0)
  /** Temporary process id -> the real id a save gave it. */
  private aliases = new Map<number, number>()
  /** Canvases to re-seed when content changes from outside them. */
  private reseedListeners = new Set<() => void>()

  setProcesses(processes: Process[]) {
    this.processes = processes
  }

  /** Starts over from what the backend just returned. */
  load(processes: Process[], revision: number) {
    this.processes = processes
    this.persisted = createPersistedState(processes, revision)
    this.aliases.clear()
  }

  /** Bases the next save on `revision` and forces it to be sent (after a conflict, "keep mine"). */
  rebase(revision: number) {
    this.persisted.revision = revision
    this.persisted.fingerprint = null
  }

  /** Records a process that was created on the backend outside of a save (e.g. "+ Subprocess"), so the next save doesn't create it again. */
  markPersisted(process: Process) {
    this.persisted.processes.set(process.id, process.type)
  }

  addAliases(idMap: Map<number, number>) {
    idMap.forEach((to, from) => this.aliases.set(from, to))
  }

  resolveId(processId: number) {
    return this.aliases.get(processId) ?? processId
  }

  subscribeReseed(listener: () => void) {
    this.reseedListeners.add(listener)
    return () => {
      this.reseedListeners.delete(listener)
    }
  }

  notifyReseed() {
    this.reseedListeners.forEach((listener) => listener())
  }
}
