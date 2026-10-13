/**
 * Client-side session token storage for the authenticated Virtual Kitchen user.
 */
const SESSION_TOKEN_KEY = 'virtual-kitchen.session.token'

export function getStoredToken(): string | null {
  return localStorage.getItem(SESSION_TOKEN_KEY)
}

export function setStoredToken(token: string): void {
  localStorage.setItem(SESSION_TOKEN_KEY, token)
}

export function clearStoredToken(): void {
  localStorage.removeItem(SESSION_TOKEN_KEY)
}

export function isAuthenticated(): boolean {
  return getStoredToken() !== null
}

const expiryListeners = new Set<() => void>()

/** Calls `listener` whenever the backend rejects the stored token; returns a function that unsubscribes it. */
export function onSessionExpired(listener: () => void): () => void {
  expiryListeners.add(listener)
  return () => {
    expiryListeners.delete(listener)
  }
}

/** The backend rejected the stored token (HTTP 401): forget it and tell the app, which signs the user out. */
export function expireSession(): void {
  clearStoredToken()
  expiryListeners.forEach((listener) => listener())
}
