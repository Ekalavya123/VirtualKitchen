/**
 * Local crash/refresh recovery for the Recipe Tool: the latest *unsaved* session snapshot of one
 * recipe, kept in localStorage until the backend confirms a save. Never the source of truth — on
 * open, RecipeSessionContext compares it with what the backend returned and only offers it when
 * they differ.
 *
 * localStorage rather than IndexedDB: a snapshot is node/edge JSON plus image URLs (generated
 * images live on the CDN, never inline), typically tens of KB, and the synchronous API is what
 * lets `pagehide` still write it while the tab is closing. Every access tolerates a missing,
 * full, blocked or corrupt store.
 */

import type { Process } from '../../../types/process'

export const RECOVERY_SCHEMA_VERSION = 1
const KEY_PREFIX = 'recipeTool.recovery.v1.'

export type StorageLike = Pick<Storage, 'getItem' | 'setItem' | 'removeItem' | 'key' | 'length'>

export type RecoverySnapshot = {
  schema: typeof RECOVERY_SCHEMA_VERSION
  recipeId: number
  /** Epoch ms of this write. */
  writtenAt: number
  /** The recipe's process revision the unsaved edits were based on (see backend RecipeTemplate.processRevision). */
  baseRevision: number
  /** Process ids known to exist on the backend at write time — tells "deleted by this user" apart from "created elsewhere since". */
  persistedIds: number[]
  processes: Process[]
}

export const recoveryKey = (recipeId: number) => `${KEY_PREFIX}${recipeId}`

/** The browser's localStorage, or null when it's unavailable (private mode, blocked site data, SSR). */
export const browserStorage = (): StorageLike | null => {
  try {
    return typeof window !== 'undefined' ? window.localStorage : null
  } catch {
    return null
  }
}

export type WriteResult = { ok: true } | { ok: false; error: string }

export const writeRecovery = (storage: StorageLike | null, snapshot: Omit<RecoverySnapshot, 'schema'>): WriteResult => {
  if (!storage) return { ok: false, error: 'Local storage is unavailable' }
  try {
    storage.setItem(recoveryKey(snapshot.recipeId), JSON.stringify({ schema: RECOVERY_SCHEMA_VERSION, ...snapshot }))
    return { ok: true }
  } catch (error) {
    // QuotaExceededError or storage disabled — the session itself is unaffected.
    return { ok: false, error: error instanceof Error ? error.message : 'Unable to write the recovery snapshot' }
  }
}

const isProcessLike = (value: unknown): value is Process => {
  if (!value || typeof value !== 'object') return false
  const process = value as Partial<Process>
  return typeof process.id === 'number'
    && (process.type === 'MAIN' || process.type === 'SUBPROCESS')
    && typeof process.name === 'string'
    && Array.isArray(process.nodes)
    && Array.isArray(process.edges)
}

const isRecoverySnapshot = (value: unknown, recipeId: number): value is RecoverySnapshot => {
  if (!value || typeof value !== 'object') return false
  const snapshot = value as Partial<RecoverySnapshot>
  return snapshot.schema === RECOVERY_SCHEMA_VERSION
    && snapshot.recipeId === recipeId
    && typeof snapshot.writtenAt === 'number'
    && typeof snapshot.baseRevision === 'number'
    && Array.isArray(snapshot.persistedIds) && snapshot.persistedIds.every((id) => typeof id === 'number')
    && Array.isArray(snapshot.processes) && snapshot.processes.every(isProcessLike)
}

/** Reads this recipe's recovery snapshot; anything unreadable (corrupt JSON, other schema, wrong shape) is removed and treated as none. */
export const readRecovery = (storage: StorageLike | null, recipeId: number): RecoverySnapshot | null => {
  if (!storage) return null
  let raw: string | null
  try {
    raw = storage.getItem(recoveryKey(recipeId))
  } catch {
    return null
  }
  if (raw == null) return null
  try {
    const parsed: unknown = JSON.parse(raw)
    if (isRecoverySnapshot(parsed, recipeId)) return parsed
  } catch {
    // fall through to removal
  }
  clearRecovery(storage, recipeId)
  return null
}

/** Removes only this recipe's snapshot. */
export const clearRecovery = (storage: StorageLike | null, recipeId: number) => {
  try {
    storage?.removeItem(recoveryKey(recipeId))
  } catch {
    // nothing to do — a blocked store has nothing we could clear either
  }
}

/** Drops recovery snapshots (of any recipe) older than `maxAgeMs`, and unreadable ones. */
export const pruneRecoveries = (storage: StorageLike | null, maxAgeMs: number, now: number = Date.now()) => {
  if (!storage) return
  try {
    const stale: string[] = []
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i)
      if (!key || !key.startsWith(KEY_PREFIX)) continue
      try {
        const parsed = JSON.parse(storage.getItem(key) ?? 'null') as Partial<RecoverySnapshot> | null
        if (!parsed || typeof parsed.writtenAt !== 'number' || now - parsed.writtenAt > maxAgeMs) stale.push(key)
      } catch {
        stale.push(key)
      }
    }
    stale.forEach((key) => storage.removeItem(key))
  } catch {
    // best effort
  }
}
