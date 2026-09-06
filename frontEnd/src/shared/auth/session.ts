/**
 * Client-side session token storage for the authenticated Virtual Kitchen user.
 */
export const SESSION_TOKEN_KEY = 'virtual-kitchen.session.token'

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
