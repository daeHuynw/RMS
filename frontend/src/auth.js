import { api, accessToken, clearTokens, refreshToken } from './api.js'

/** Current user from /auth/me, or null if logged out / invalid. */
export async function fetchMe() {
  if (!accessToken()) {
    return null
  }
  try {
    return await api('/auth/me')
  } catch {
    clearTokens()
    return null
  }
}

export function hasAnyRole(me, roles) {
  if (!me?.roles?.length) {
    return false
  }
  if (!roles || roles.length === 0) {
    return true
  }
  return roles.some((role) => me.roles.includes(role))
}

/** Prefer hub home; staff tools still reachable from home cards. */
export function homePathFor(_me) {
  return '/home.html'
}

export async function logoutAndRedirect(to = '/') {
  const currentRefresh = refreshToken()
  try {
    if (currentRefresh && accessToken()) {
      await api('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: currentRefresh }),
      })
    }
  } catch {
    // still clear local session
  }
  clearTokens()
  window.location.href = to
}

/**
 * Ensure user has one of allowed roles. Redirects to login (or home) otherwise.
 * @returns {Promise<object>} me
 */
export async function requireRoles(allowedRoles, { loginRedirect = '/', forbiddenRedirect = '/home.html' } = {}) {
  if (!accessToken()) {
    window.location.href = loginRedirect
    throw new Error('Unauthorized')
  }
  const me = await fetchMe()
  if (!me) {
    window.location.href = loginRedirect
    throw new Error('Unauthorized')
  }
  if (allowedRoles?.length && !hasAnyRole(me, allowedRoles)) {
    window.location.href = forbiddenRedirect
    throw new Error('Forbidden')
  }
  return me
}
