import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './app/App'
import { NotificationProvider } from './shared/components/notifications/NotificationProvider'
import { ThemeProvider } from './shared/theme/ThemeProvider'
import './App.css'

import './styles/global.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <NotificationProvider>
        <App />
      </NotificationProvider>
    </ThemeProvider>
  </React.StrictMode>,
)