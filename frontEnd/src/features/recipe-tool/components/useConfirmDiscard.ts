import { useCallback } from 'react'
import { useConfirm } from '../../../shared/components/confirm/confirmContext'

/**
 * Discarding unsaved input is a deletion too: returns `confirmDiscard(what, discard)`, which asks
 * before calling `discard` — `what` names what will be lost, e.g. "subprocess name and description".
 */
export function useConfirmDiscard() {
  const confirm = useConfirm()
  return useCallback(async (what: string, discard: () => void) => {
    const confirmed = await confirm({
      eyebrow: 'Discard changes',
      title: 'Discard what you typed?',
      message: `Your unsaved ${what} will be lost. This can't be undone.`,
      confirmLabel: 'Discard',
    })
    if (confirmed) discard()
  }, [confirm])
}
