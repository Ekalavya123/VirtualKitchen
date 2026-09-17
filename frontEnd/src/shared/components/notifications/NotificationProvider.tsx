import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import './NotificationProvider.css'

type NotificationType = 'success' | 'error' | 'info'

type NotificationItem = {
  id: number
  message: string
  type: NotificationType
}

type NotificationContextValue = {
  notifySuccess: (message: string) => void
  notifyError: (message: string) => void
  /** For neutral/informational notices — e.g. "using the standard model, premium credits are exhausted" — that are neither a success nor an error. */
  notifyInfo: (message: string) => void
}

const AUTO_DISMISS_MS = 4000

const NotificationContext = createContext<NotificationContextValue | null>(null)

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<NotificationItem[]>([])
  const nextId = useRef(0)

  const dismiss = useCallback((id: number) => {
    setNotifications(current => current.filter(item => item.id !== id))
  }, [])

  const notify = useCallback((message: string, type: NotificationType) => {
    const id = nextId.current++

    setNotifications(current => [...current, { id, message, type }])
    window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS)
  }, [dismiss])

  const notifySuccess = useCallback((message: string) => notify(message, 'success'), [notify])
  const notifyError = useCallback((message: string) => notify(message, 'error'), [notify])
  const notifyInfo = useCallback((message: string) => notify(message, 'info'), [notify])

  return (
    <NotificationContext.Provider value={{ notifySuccess, notifyError, notifyInfo }}>
      {children}

      <div className="notification-container" role="status" aria-live="polite">
        {notifications.map(item => (
          <div
            key={item.id}
            className={`notification-toast notification-toast-${item.type}`}
          >
            <span className="notification-toast-icon" aria-hidden="true">
              {item.type === 'success' ? '✅' : item.type === 'error' ? '⚠️' : 'ℹ️'}
            </span>

            <span className="notification-toast-message">{item.message}</span>

            <button
              onClick={() => dismiss(item.id)}
              className="notification-toast-close"
              aria-label="Dismiss notification"
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </NotificationContext.Provider>
  )
}

export function useNotifications() {
  const context = useContext(NotificationContext)

  if (!context) {
    throw new Error('useNotifications must be used within a NotificationProvider')
  }

  return context
}
