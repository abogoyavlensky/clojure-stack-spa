import { queryOptions } from '@tanstack/react-query'

/** Error thrown by the API client on non-2xx responses. */
export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, statusText: string, body: unknown) {
    super(`API request failed: ${status} ${statusText}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * Fetch a JSON endpoint of the backend. Requests are same-origin: in
 * development the Vite dev server proxies /api to the backend.
 *
 * The X-Requested-With header marks the request as coming from the app; the
 * backend rejects unsafe requests (POST, PUT, ...) that have neither this
 * header nor a JSON body, which protects the session cookie against CSRF.
 */
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Requested-With', 'fetch')
  if (init?.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(path, { ...init, headers })
  const body = response.headers.get('Content-Type')?.includes('json')
    ? await response.json()
    : await response.text()
  if (!response.ok) {
    throw new ApiError(response.status, response.statusText, body)
  }
  return body as T
}

export interface Status {
  status: string
  sqlite_version: string
}

export const statusQuery = queryOptions({
  queryKey: ['status'],
  queryFn: () => request<Status>('/api/status'),
})
