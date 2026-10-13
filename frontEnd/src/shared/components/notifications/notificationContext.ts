import { createContext, useContext } from 'react'

type NotificationContextValue = {
  notifySuccess: (message: string) => void
  notifyError: (message: string) => void
  /** For neutral/informational notices — e.g. "using the standard model, premium credits are exhausted" — that are neither a success nor an error. */
  notifyInfo: (message: string) => void
}

export const NotificationContext = createContext<NotificationContextValue | null>(null)

/** Toasts: `notifySuccess`, `notifyError` and `notifyInfo` show a message that dismisses itself. */
export function useNotifications() {
  const context = useContext(NotificationContext)

  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider')
  }

  return context
}
