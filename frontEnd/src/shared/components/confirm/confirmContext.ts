import { createContext, useContext } from 'react'

export type ConfirmOptions = {
  /** Small uppercase label above the title, e.g. "Delete recipe". */
  eyebrow?: string
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  /** 'danger' (default) styles the confirm button as destructive. */
  tone?: 'danger' | 'default'
}

/** Resolves true when the user confirms, false when they cancel or dismiss. */
export type ConfirmFn = (options: ConfirmOptions) => Promise<boolean>

export const ConfirmContext = createContext<ConfirmFn | null>(null)

/**
 * Asks the user to confirm an action — every deletion in the app goes through this — e.g.
 * `if (!(await confirm({ title: 'Delete this recipe?', message: '…' }))) return`.
 */
export function useConfirm(): ConfirmFn {
  const confirm = useContext(ConfirmContext)
  if (!confirm) throw new Error('useConfirm must be used within a ConfirmProvider')
  return confirm
}
