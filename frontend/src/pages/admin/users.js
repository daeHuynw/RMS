import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const listEl = document.querySelector('#user-list')
const form = document.querySelector('#create-form')

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

async function load() {
  const users = await api('/admin/users')
  listEl.innerHTML = ''
  loadingEl.hidden = true
  for (const u of users) {
    const card = document.createElement('article')
    card.className = 'order-card'
    const roleOpts = ['CUSTOMER', 'STAFF', 'KITCHEN', 'ADMIN']
      .map(
        (r) =>
          `<label><input type="checkbox" data-role="${r}" ${u.roles.includes(r) ? 'checked' : ''}/> ${r}</label>`,
      )
      .join(' ')
    card.innerHTML = `
      <div class="order-card-head">
        <strong>${escapeHtml(u.fullName)}</strong>
        <span class="badge ${u.active ? 'badge-on' : 'badge-off'}">${u.active ? 'Active' : 'Off'}</span>
      </div>
      <p class="muted">${escapeHtml(u.email)} · ${escapeHtml(u.phone || '—')}</p>
      <p class="muted">Hiện: ${escapeHtml(u.roles.join(', '))}</p>
      <div class="role-box" data-user="${u.id}">${roleOpts}</div>
      <div class="admin-actions" style="margin-top:8px">
        <button type="button" data-save-roles="${u.id}">Lưu roles</button>
        <button type="button" class="${u.active ? 'btn-danger' : ''}" data-toggle="${u.id}" data-active="${u.active}">
          ${u.active ? 'Khóa' : 'Mở'}
        </button>
      </div>
    `
    listEl.append(card)
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    await api('/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        fullName: form.fullName.value.trim(),
        email: form.email.value.trim(),
        password: form.password.value,
        phone: form.phone.value.trim() || null,
        roles: [form.roles.value],
      }),
    })
    flash('Đã tạo user', false)
    form.reset()
    await load()
  } catch (err) {
    flash(err.message || 'Tạo thất bại', true)
  }
})

listEl.addEventListener('click', async (event) => {
  const saveId = event.target.getAttribute?.('data-save-roles')
  const toggleId = event.target.getAttribute?.('data-toggle')
  try {
    if (saveId) {
      const box = listEl.querySelector(`.role-box[data-user="${saveId}"]`)
      const roles = [...box.querySelectorAll('input[data-role]:checked')].map((el) => el.getAttribute('data-role'))
      if (!roles.length) {
        flash('Chọn ít nhất một role', true)
        return
      }
      await api(`/admin/users/${saveId}/roles`, {
        method: 'PUT',
        body: JSON.stringify({ roles }),
      })
      flash('Đã cập nhật roles', false)
      await load()
    }
    if (toggleId) {
      const active = event.target.getAttribute('data-active') === 'true'
      await api(`/admin/users/${toggleId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ active: !active }),
      })
      flash(active ? 'Đã khóa' : 'Đã mở', false)
      await load()
    }
  } catch (err) {
    flash(err.message || 'Thao tác thất bại', true)
  }
})

await requireAdmin()
await load()
