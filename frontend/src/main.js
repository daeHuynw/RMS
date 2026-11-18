import { api, saveTokens, clearTokens, accessToken } from './api.js'
import { fetchMe, homePathFor } from './auth.js'
import { mountAppNav } from './nav.js'

const form = document.querySelector('#login-form')
const error = document.querySelector('#error')

const me = await mountAppNav()
if (accessToken() && me) {
  window.location.href = homePathFor(me)
}

// Phân biệt lỗi đăng nhập: chỉ 401 mới là sai email/mật khẩu (backend cố ý trả cùng
// 401 cho tài khoản bị khoá để không lộ email nào tồn tại).
function loginErrorMessage(err) {
  if (err.status == null) {
    return 'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.'
  }
  if (err.status === 401) {
    return 'Sai email hoặc mật khẩu (hoặc tài khoản đã bị khoá).'
  }
  if (err.status === 429) {
    const minutes = Math.max(1, Math.ceil((err.retryAfterSeconds || 900) / 60))
    return `Đăng nhập sai quá nhiều lần. Thử lại sau ${minutes} phút.`
  }
  if (err.status === 400) {
    return 'Email hoặc mật khẩu không đúng định dạng.'
  }
  if (err.status >= 500) {
    return 'Máy chủ đang lỗi. Thử lại sau ít phút.'
  }
  return err.message || 'Đăng nhập thất bại.'
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  error.hidden = true
  try {
    const login = await api('/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        email: form.email.value.trim(),
        password: form.password.value,
      }),
    })
    saveTokens(login.accessToken, login.refreshToken)
    const user = await fetchMe()
    window.location.href = homePathFor(user)
  } catch (err) {
    clearTokens()
    error.textContent = loginErrorMessage(err)
    error.hidden = false
  }
})
