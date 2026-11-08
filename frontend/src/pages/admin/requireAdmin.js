import { api, accessToken, clearTokens } from '../../api.js'

/** Redirect to login unless current user has ADMIN. Returns profile. */
export async function requireAdmin() {
  if (!accessToken()) {
    window.location.href = '/'
    throw new Error('Unauthorized')
  }
  try {
    const me = await api('/auth/me')
    if (!me.roles?.includes('ADMIN')) {
      clearTokens()
      window.location.href = '/'
      throw new Error('Forbidden')
    }
    return me
  } catch (err) {
    clearTokens()
    window.location.href = '/'
    throw err
  }
}
