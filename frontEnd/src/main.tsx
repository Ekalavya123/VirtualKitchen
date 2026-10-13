import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './app/App'
import { NotificationProvider } from './shared/components/notifications/NotificationProvider'
import { ConfirmProvider } from './shared/components/confirm/ConfirmProvider'
import { JobTrackerProvider } from './features/recipe-tool/context/JobTrackerContext'
import { ThemeProvider } from './shared/theme/ThemeProvider'

import './styles/global.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <NotificationProvider>
        <ConfirmProvider>
          <JobTrackerProvider>
            <App />
          </JobTrackerProvider>
        </ConfirmProvider>
      </NotificationProvider>
    </ThemeProvider>
  </React.StrictMode>,
)