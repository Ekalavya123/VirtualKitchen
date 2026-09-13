import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { getPreferredTheme, getStoredTheme, setStoredTheme, type Theme } from './theme'

type ThemeContextValue = {
  theme: Theme
  toggleTheme: () => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function applyTheme(theme: Theme) {
  document.documentElement.setAttribute('data-theme', theme)
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => getStoredTheme() ?? getPreferredTheme())

  useEffect(() => {
    applyTheme(theme)
    setStoredTheme(theme)
  }, [theme])

  const toggleTheme = () => {
    setTheme(current => (current === 'dark' ? 'light' : 'dark'))
  }

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const context = useContext(ThemeContext)

  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider')
  }

  return context
}
