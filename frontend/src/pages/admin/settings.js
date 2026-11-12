import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const form = document.querySelector('#settings-form')
const updatedAt = document.querySelector('#updatedAt')

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

async function load() {
  const s = await api('/admin/settings')
  form.taxRate.value = s.taxRate
  form.reservationDurationMinutes.value = s.reservationDurationMinutes
  updatedAt.textContent = s.updatedAt
    ? `Cập nhật lần cuối: ${new Date(s.updatedAt).toLocaleString('vi-VN')}`
    : ''
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    await api('/admin/settings', {
      method: 'PUT',
      body: JSON.stringify({
        taxRate: Number(form.taxRate.value),
        reservationDurationMinutes: Number(form.reservationDurationMinutes.value),
      }),
    })
    flash('Đã lưu cài đặt', false)
    await load()
  } catch (err) {
    flash(err.message || 'Lưu thất bại', true)
  }
})

await requireAdmin()
await load()
