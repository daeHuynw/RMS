import { api } from '../../api.js'
import { requireStaff } from '../staff/requireStaff.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#res-table')
const bodyEl = document.querySelector('#res-body')

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function fmt(iso) {
  return new Date(iso).toLocaleString('vi-VN')
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function actionsFor(status, id) {
  const btn = (label, path, method = 'POST') =>
    `<button type="button" data-path="${path}" data-method="${method}">${label}</button>`
  if (status === 'PENDING') {
    return `
      ${btn('Xác nhận', `/staff/reservations/${id}/confirm`, 'PATCH')}
      ${btn('Từ chối', `/staff/reservations/${id}/reject`, 'PATCH')}
    `
  }
  if (status === 'CONFIRMED') {
    return `
      ${btn('Check-in', `/staff/reservations/${id}/check-in`)}
      ${btn('No-show', `/staff/reservations/${id}/no-show`)}
    `
  }
  if (status === 'CHECKED_IN') {
    return btn('Hoàn tất', `/staff/reservations/${id}/complete`)
  }
  return ''
}

async function load() {
  const rows = await api('/staff/reservations')
  bodyEl.innerHTML = ''
  for (const r of rows) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td>${escapeHtml(r.tableCode)}</td>
      <td>
        ${escapeHtml(r.guestName || '')}<br />
        <span class="muted">${escapeHtml(r.guestPhone || '')} · ${r.guestCount} khách</span>
      </td>
      <td class="muted">${fmt(r.start)}<br />→ ${fmt(r.end)}</td>
      <td><span class="badge">${escapeHtml(r.status)}</span></td>
      <td><div class="admin-actions">${actionsFor(r.status, r.id)}</div></td>
    `
    bodyEl.append(tr)
  }
  loadingEl.hidden = true
  tableEl.hidden = false
}

bodyEl.addEventListener('click', async (event) => {
  const path = event.target.getAttribute?.('data-path')
  const method = event.target.getAttribute?.('data-method') || 'POST'
  if (!path) return
  try {
    await api(path, { method, body: method === 'GET' ? undefined : '{}' })
    flash('Đã cập nhật', false)
    await load()
  } catch (err) {
    flash(err.message || 'Thao tác thất bại', true)
  }
})

await requireStaff()
await load()
