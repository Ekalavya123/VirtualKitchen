/**
 * Client-side theme preference storage for the Virtual Kitchen UI.
 */
export const THEME_KEY = 'virtual-kitchen.theme'

export type Theme = 'light' | 'dark'

export function getStoredTheme(): Theme | null {
  const value = localStorage.getItem(THEME_KEY)
  return value === 'light' || value === 'dark' ? value : null
}

export function setStoredTheme(theme: Theme): void {
  localStorage.setItem(THEME_KEY, theme)
}

export function getPreferredTheme(): Theme {
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}
