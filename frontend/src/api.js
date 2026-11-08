const ACCESS = 'accessToken'
const REFRESH = 'refreshToken'

export function saveTokens(accessToken, refreshToken) {
  localStorage.setItem(ACCESS, accessToken)
  localStorage.setItem(REFRESH, refreshToken)
}

export function accessToken() {
  return localStorage.getItem(ACCESS)
}

export function refreshToken() {
  return localStorage.getItem(REFRESH)
}

export function clearTokens() {
  localStorage.removeItem(ACCESS)
  localStorage.removeItem(REFRESH)
}

export async function api(path, options = {}) {
  const headers = new Headers(options.headers)
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = accessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`/api/v1${path}`, { ...options, headers })
  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(data?.message || 'Request failed')
    error.status = response.status
    throw error
  }
  return data
}
