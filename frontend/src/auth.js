import { api, accessToken, clearTokens, refreshToken } from './api.js'

// Một trang chỉ gọi /auth/me một lần: nav và requireRoles dùng chung kết quả.
let mePromise = null

/**
 * User hiện tại từ /auth/me; null nếu chưa đăng nhập hoặc phiên hết hạn (401).
 * Lỗi khác (mất mạng, 5xx) thì GIỮ token và ném lỗi — không coi là đăng xuất.
 */
export function fetchMe() {
  if (!accessToken()) {
    return Promise.resolve(null)
  }
  if (!mePromise) {
    mePromise = loadMe()
  }
  return mePromise
}

async function loadMe() {
  try {
    return await api('/auth/me')
  } catch (err) {
    // Chỉ nhớ kết quả thành công: sau 401 (token đã xoá) hay lỗi mạng, lần gọi sau hỏi lại
    // — ví dụ trang login vừa lưu token mới.
    mePromise = null
    if (err.status === 401) {
      clearTokens()
      return null
    }
    throw err
  }
}

export const SERVER_UNREACHABLE = 'Không tải được thông tin tài khoản (mất mạng hoặc máy chủ lỗi). Tải lại trang sau ít phút.'

/** Dòng báo lỗi đầu trang, dùng khi chưa biết người dùng là ai. */
export function showPageError(message) {
  let el = document.querySelector('#page-error')
  if (!el) {
    el = document.createElement('p')
    el.id = 'page-error'
    el.className = 'error'
    const nav = document.querySelector('#app-nav')
    if (nav) nav.after(el)
    else document.body.prepend(el)
  }
  el.textContent = message
}

/** Promise không bao giờ xong: dừng script trang sau khi đã chuyển hướng / báo lỗi, không ném lỗi. */
function halt() {
  return new Promise(() => {})
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
  let me
  try {
    me = await fetchMe()
  } catch {
    showPageError(SERVER_UNREACHABLE)
    return halt()
  }
  if (!me) {
    window.location.href = loginRedirect
    return halt()
  }
  if (allowedRoles?.length && !hasAnyRole(me, allowedRoles)) {
    window.location.href = forbiddenRedirect
    return halt()
  }
  return me
}
