import { api, saveTokens, clearTokens, accessToken } from './api.js'
import { fetchMe, homePathFor } from './auth.js'
import { mountAppNav } from './nav.js'

const form = document.querySelector('#login-form')
const error = document.querySelector('#error')

await mountAppNav()

if (accessToken()) {
  const me = await fetchMe()
  if (me) {
    window.location.href = homePathFor(me)
  }
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
    const me = await api('/auth/me')
    window.location.href = homePathFor(me)
  } catch {
    clearTokens()
    error.textContent = 'Sai email hoặc mật khẩu.'
    error.hidden = false
  }
})
