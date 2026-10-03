/**
 * Generic HTTP Client
 * Wraps fetch API with common error handling and configuration
 */

import { API_CONFIG } from './config'
import { getStoredToken } from '../shared/auth/session'

interface FetchOptions extends RequestInit {
  params?: Record<string, string | number | boolean>
  timeout?: number
}

/**
 * A request that reached the server and got a non-2xx answer. Keeps the HTTP status so callers can
 * tell e.g. a validation error (422) or a conflict (409) from an outage (5xx) — a request that
 * never got a response (offline, timeout) throws a plain Error instead.
 */
export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/** The HTTP status of a failed request, or undefined when it never got a response. */
export const httpStatusOf = (error: unknown): number | undefined =>
  error instanceof ApiError ? error.status : undefined

interface ApiResponse<T> {
  success: boolean
  message?: string
  data: T
  timestamp?: string
}

/**
 * Build URL with query parameters
 */
function buildUrl(endpoint: string, params?: Record<string, string | number | boolean>): string {
  const url = new URL(endpoint, API_CONFIG.baseURL)

  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      url.searchParams.append(key, String(value))
    })
  }

  return url.toString()
}

/**
 * Generic HTTP request method
 */
async function request<T>(
  endpoint: string,
  options: FetchOptions = {}
): Promise<T> {
  const { params, timeout = API_CONFIG.timeout, ...fetchOptions } = options

  const url = buildUrl(endpoint, params)
  const controller = new AbortController()
  const externalSignal = fetchOptions.signal

  if (externalSignal?.aborted) {
    controller.abort(externalSignal.reason)
  } else if (externalSignal) {
    externalSignal.addEventListener('abort', () => controller.abort(externalSignal.reason), { once: true })
  }

  const timeoutId = timeout > 0
    ? window.setTimeout(() => controller.abort(new DOMException('Request timed out', 'AbortError')), timeout)
    : null

  let response: Response

  try {
    response = await fetch(url, {
      ...defaultFetchOptions,
      ...fetchOptions,
      signal: controller.signal,
      headers: {
        ...API_CONFIG.headers,
        ...authHeader(),
        ...fetchOptions.headers,
      },
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new Error('Request timed out. Please try again.')
    }

    throw error
  } finally {
    if (timeoutId != null) {
      window.clearTimeout(timeoutId)
    }
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new ApiError(
      errorData.message ||
      `HTTP ${response.status}: ${response.statusText}`,
      response.status,
    )
  }

  const raw = await response.json().catch(() => (null as any))

  // Support two response shapes:
  // 1) API wrapper: { success: boolean, message?: string, data: T }
  // 2) Raw payload: T
  if (raw && typeof raw === 'object' && 'data' in raw) {
    return (raw as ApiResponse<T>).data
  }

  return raw as T
}

/**
 * GET request
 */
export async function apiGet<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  return request<T>(endpoint, {
    ...options,
    method: 'GET',
  })
}

/**
 * POST request
 */
export async function apiPost<T>(
  endpoint: string,
  body?: unknown,
  options?: FetchOptions
): Promise<T> {
  return request<T>(endpoint, {
    ...options,
    method: 'POST',
    body: body ? JSON.stringify(body) : undefined,
  })
}

/**
 * PUT request
 */
export async function apiPut<T>(
  endpoint: string,
  body?: unknown,
  options?: FetchOptions
): Promise<T> {
  return request<T>(endpoint, {
    ...options,
    method: 'PUT',
    body: body ? JSON.stringify(body) : undefined,
  })
}

/**
 * PATCH request
 */
export async function apiPatch<T>(
  endpoint: string,
  body?: unknown,
  options?: FetchOptions
): Promise<T> {
  return request<T>(endpoint, {
    ...options,
    method: 'PATCH',
    body: body ? JSON.stringify(body) : undefined,
  })
}

/**
 * DELETE request
 */
export async function apiDelete<T>(
  endpoint: string,
  options?: FetchOptions
): Promise<T> {
  return request<T>(endpoint, {
    ...options,
    method: 'DELETE',
  })
}

const defaultFetchOptions: RequestInit = {
  headers: API_CONFIG.headers,
}

/**
 * Attaches the stored session token (if any) as a Bearer Authorization header.
 */
function authHeader(): Record<string, string> {
  const token = getStoredToken()

  return token ? { Authorization: `Bearer ${token}` } : {}
}
