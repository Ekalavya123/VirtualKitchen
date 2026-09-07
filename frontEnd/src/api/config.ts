/**
 * API Configuration
 * Centralized configuration for all API communication
 */

export const API_CONFIG = {
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 300000,
  headers: {
    'Content-Type': 'application/json',
  },
}

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || ''

/**
 * Generic fetch options
 */
export const defaultFetchOptions: RequestInit = {
  headers: API_CONFIG.headers,
}
