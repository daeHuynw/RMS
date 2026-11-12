import { api } from '../../api.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const form = document.querySelector('#register-form')
const error = document.querySelector('#error')

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  error.hidden = true
  try {
    await api('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        fullName: form.fullName.value.trim(),
        email: form.email.value.trim(),
        phone: form.phone.value.trim() || null,
        password: form.password.value,
      }),
    })
    window.location.href = '/'
  } catch (err) {
    error.textContent = err.message || 'Đăng ký thất bại'
    error.hidden = false
  }
})