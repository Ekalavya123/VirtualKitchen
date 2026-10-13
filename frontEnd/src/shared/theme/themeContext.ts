import { createContext, useContext } from 'react'
import type { Theme } from './theme'

type ThemeContextValue = {
  theme: Theme
  toggleTheme: () => void
}

export const ThemeContext = createContext<ThemeContextValue | null>(null)

/** The current light/dark theme and a toggle; the choice is remembered in this browser. */
export function useTheme() {
  const context = useContext(ThemeContext)

  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }

  return context
}
