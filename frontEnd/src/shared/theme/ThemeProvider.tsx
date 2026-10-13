import { useEffect, useState, type ReactNode } from 'react'
import { getPreferredTheme, getStoredTheme, setStoredTheme, type Theme } from './theme'
import { ThemeContext } from './themeContext'

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
