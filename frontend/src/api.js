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

let refreshInFlight = null

async function refreshAccessToken() {
  if (refreshInFlight) {
    return refreshInFlight
  }

  const current = refreshToken()
  if (!current) {
    return false
  }

  refreshInFlight = (async () => {
    try {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: current }),
      })
      const data = await response.json().catch(() => null)
      if (!response.ok) {
        // Chỉ xóa khi token storage vẫn là token vừa gửi (tránh race xoay token).
        if (refreshToken() === current) {
          clearTokens()
        }
        return false
      }
      saveTokens(data.accessToken, data.refreshToken)
      return true
    } catch {
      return false
    }
  })()

  try {
    return await refreshInFlight
  } finally {
    refreshInFlight = null
  }
}

export async function api(path, options = {}) {
  const send = async () => {
    const headers = new Headers(options.headers)
    if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
      headers.set('Content-Type', 'application/json')
    }
    const token = accessToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    return fetch(`/api/v1${path}`, { ...options, headers })
  }

  let response = await send()

  const canRetry =
    response.status === 401
    && path !== '/auth/login'
    && path !== '/auth/register'
    && path !== '/auth/refresh'

  if (canRetry) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      response = await send()
    }
  }

  if (response.status === 204) {
    return null
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const error = new Error(data?.message || 'Request failed')
    error.status = response.status
    error.code = data?.code
    error.path = data?.path
    throw error
  }
  return data
}

/** Download binary (PDF…) with same auth/refresh as api(). */
export async function apiBlob(path, options = {}) {
  const send = async () => {
    const headers = new Headers(options.headers)
    const token = accessToken()
    if (token) {
      headers.set('Authorization', `Bearer ${token}`)
    }
    return fetch(`/api/v1${path}`, { ...options, headers })
  }

  let response = await send()
  if (response.status === 401) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      response = await send()
    }
  }
  if (!response.ok) {
    const data = await response.json().catch(() => null)
    const error = new Error(data?.message || 'Request failed')
    error.status = response.status
    error.code = data?.code
    error.path = data?.path
    throw error
  }
  return response.blob()
}
