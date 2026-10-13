import { useCallback, useContext, useSyncExternalStore } from 'react'
import { JobTrackerContext, type JobKey, type TrackedJobFor } from './jobTracker'

export function useJobTracker() {
  const tracker = useContext(JobTrackerContext)
  if (!tracker) throw new Error('useJobTracker must be used inside a JobTrackerProvider')
  return tracker
}

/** The tracked job under `key` (undefined if none); re-renders only when that job changes. */
export function useTrackedJob<K extends JobKey>(key: K): TrackedJobFor<K> | undefined {
  const tracker = useJobTracker()
  const getSnapshot = useCallback(() => tracker.get(key), [tracker, key])
  return useSyncExternalStore(tracker.subscribe, getSnapshot) as TrackedJobFor<K> | undefined
}

/** Bumps whenever a tracked job finishes — e.g. to refetch the AI credit balance. */
export function useJobsFinishedSignal() {
  const tracker = useJobTracker()
  return useSyncExternalStore(tracker.subscribe, tracker.getFinishedCount)
}
