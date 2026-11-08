import { api, saveTokens, clearTokens, accessToken, refreshToken } from './api.js'

const form = document.querySelector('#login-form')
const error = document.querySelector('#error')
const profile = document.querySelector('#profile')

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
    showProfile(await api('/auth/me'))
  } catch {
    clearTokens()
    error.textContent = 'Sai email hoặc mật khẩu.'
    error.hidden = false
  }
})

document.querySelector('#logout').addEventListener('click', async () => {
  const currentRefresh = refreshToken()
  try {
    if (currentRefresh && accessToken()) {
      await api('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: currentRefresh }),
      })
    }
  } catch {
    // Phiên trên trình duyệt vẫn được xóa khi server đã thu hồi token.
  }
  clearTokens()
  profile.hidden = true
  form.hidden = false
  form.password.value = ''
})

function showProfile(me) {
  document.querySelector('#greeting').textContent = `Xin chào, ${me.fullName}`
  document.querySelector('#profile-email').textContent = me.email
  document.querySelector('#profile-roles').textContent = me.roles.join(', ')
  form.hidden = true
  profile.hidden = false
}

if (accessToken()) {
  api('/auth/me').then(showProfile).catch(() => clearTokens())
}
