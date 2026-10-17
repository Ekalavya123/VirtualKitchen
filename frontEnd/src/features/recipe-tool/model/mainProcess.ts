/**
 * A recipe has one MAIN process. Creating it twice at the same moment (e.g. a page opened twice in quick
 * succession) can briefly leave two on the backend, which then deletes the one it didn't link. A load that lands in
 * that window must not keep the doomed one: it would be sent with every later save and fail it ("Process not
 * found"). So only the MAIN process the recipe is linked to is kept — or, when none is linked yet, the oldest.
 *
 * Pure, so it runs under node --test.
 */

import type { Process } from '../../../types/process'

export const withoutStrayMainProcesses = <T extends Pick<Process, 'id' | 'type'>>(
  processes: readonly T[],
  mainProcessId: number | null | undefined,
): T[] => {
  const mains = processes.filter((process) => process.type === 'MAIN')
  if (mains.length <= 1) return [...processes]
  const linked = mainProcessId != null ? mains.find((process) => process.id === mainProcessId) : undefined
  const keep = linked ?? mains.reduce((oldest, process) => (process.id < oldest.id ? process : oldest))
  return processes.filter((process) => process.type !== 'MAIN' || process === keep)
}
