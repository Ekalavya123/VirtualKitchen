import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './app/App'
import { NotificationProvider } from './shared/components/notifications/NotificationProvider'
import './App.css'

import './styles/global.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <NotificationProvider>
      <App />
    </NotificationProvider>
  </React.StrictMode>,
)