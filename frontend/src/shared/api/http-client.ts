type ProblemDetail = {
  title?: string
  detail?: string
  status?: number
}

export class HttpError extends Error {
  readonly status: number
  readonly detail?: string

  constructor(message: string, status: number, detail?: string) {
    super(message)
    this.name = 'HttpError'
    this.status = status
    this.detail = detail
  }
}

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const apiBaseUrl = (configuredBaseUrl || '/api').replace(/\/+$/, '')

export function resolveApiUrl(path: string) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return new URL(`${apiBaseUrl}${normalizedPath}`, window.location.origin).toString()
}

export async function requestJson<T = unknown>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers)
  headers.set('Accept', 'application/json')

  const response = await fetch(resolveApiUrl(path), {
    ...init,
    headers,
  })

  const body = await response.text()

  if (response.ok) {
    return (body ? JSON.parse(body) : undefined) as T
  }

  let problem: ProblemDetail | undefined

  if (body) {
    try {
      problem = JSON.parse(body) as ProblemDetail
    } catch {
      problem = undefined
    }
  }

  const detail = problem?.detail ?? problem?.title
  throw new HttpError(detail ?? `A API respondeu com status ${response.status}.`, response.status, detail)
}

export function swrFetcher(key: unknown) {
  if (typeof key !== 'string') {
    throw new TypeError('A chave remota do SWR deve ser uma string.')
  }

  return requestJson(key)
}
