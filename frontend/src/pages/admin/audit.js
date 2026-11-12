import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#audit-table')
const bodyEl = document.querySelector('#audit-body')

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

await requireAdmin()
try {
  const rows = await api('/admin/audit')
  bodyEl.innerHTML = ''
  for (const row of rows) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td class="muted">${new Date(row.createdAt).toLocaleString('vi-VN')}</td>
      <td>${row.actorId ?? '—'}</td>
      <td><strong>${escapeHtml(row.action)}</strong></td>
      <td class="muted">${escapeHtml(row.targetType)} ${escapeHtml(row.targetId || '')}</td>
      <td class="muted">${escapeHtml(row.detail || '')}</td>
    `
    bodyEl.append(tr)
  }
  loadingEl.hidden = true
  tableEl.hidden = false
} catch (err) {
  loadingEl.hidden = true
  errorEl.hidden = false
  errorEl.textContent = err.message || 'Không tải được audit'
}
