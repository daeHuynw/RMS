import { api, accessToken, clearTokens } from '../../api.js'

if (!accessToken()) {
  window.location.href = '/'
}

const error = document.querySelector('#error')
const ok = document.querySelector('#ok')
const profileForm = document.querySelector('#profile-form')
const passwordForm = document.querySelector('#password-form')

function flash(message, isError) {
  error.hidden = !isError
  ok.hidden = isError
  ;(isError ? error : ok).textContent = message
}

async function load() {
  try {
    const me = await api('/auth/me')
    profileForm.fullName.value = me.fullName
    profileForm.email.value = me.email
    profileForm.phone.value = me.phone || ''
    document.querySelector('#roles').textContent = me.roles.join(', ')
  } catch {
    clearTokens()
    window.location.href = '/'
  }
}

profileForm.addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    await api('/auth/me', {
      method: 'PUT',
      body: JSON.stringify({
        fullName: profileForm.fullName.value.trim(),
        phone: profileForm.phone.value.trim() || null,
      }),
    })
    flash('Đã lưu hồ sơ', false)
  } catch (err) {
    flash(err.message || 'Lưu thất bại', true)
  }
})

passwordForm.addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    await api('/auth/me/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword: passwordForm.currentPassword.value,
        newPassword: passwordForm.newPassword.value,
      }),
    })
    passwordForm.reset()
    flash('Đã đổi mật khẩu', false)
  } catch (err) {
    flash(err.message || 'Đổi mật khẩu thất bại', true)
  }
})

load()
