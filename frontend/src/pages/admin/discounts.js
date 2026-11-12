import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#discount-table')
const bodyEl = document.querySelector('#discount-body')
const form = document.querySelector('#discount-form')
const formTitle = document.querySelector('#form-title')
const resetBtn = document.querySelector('#reset-btn')

let rows = []

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

function toLocalInput(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toIso(localValue) {
  return new Date(localValue).toISOString()
}

function resetForm() {
  form.discountId.value = ''
  form.code.value = ''
  form.description.value = ''
  form.type.value = 'PERCENTAGE'
  form.value.value = ''
  form.minimumOrderAmount.value = ''
  form.maximumDiscountAmount.value = ''
  form.usageLimit.value = ''
  form.startAt.value = ''
  form.endAt.value = ''
  formTitle.textContent = 'Thêm mã'
}

function payloadFromForm() {
  return {
    code: form.code.value.trim(),
    description: form.description.value.trim() || null,
    type: form.type.value,
    value: Number(form.value.value),
    minimumOrderAmount: form.minimumOrderAmount.value
      ? Number(form.minimumOrderAmount.value)
      : null,
    maximumDiscountAmount: form.maximumDiscountAmount.value
      ? Number(form.maximumDiscountAmount.value)
      : null,
    usageLimit: form.usageLimit.value ? Number(form.usageLimit.value) : null,
    startAt: toIso(form.startAt.value),
    endAt: toIso(form.endAt.value),
  }
}

function render() {
  bodyEl.innerHTML = ''
  for (const d of rows) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td>
        <strong>${escapeHtml(d.code)}</strong><br />
        <span class="muted">${escapeHtml(d.description || '')}</span>
      </td>
      <td class="muted">${escapeHtml(d.type)} · ${d.value}</td>
      <td class="muted">${d.usedCount}${d.usageLimit != null ? ` / ${d.usageLimit}` : ''}</td>
      <td><span class="badge ${d.active ? 'badge-on' : 'badge-off'}">${d.active ? 'Active' : 'Off'}</span></td>
      <td>
        <div class="admin-actions">
          <button type="button" class="btn-secondary" data-edit="${d.id}">Sửa</button>
          <button type="button" data-toggle="${d.id}" class="${d.active ? 'btn-danger' : ''}">
            ${d.active ? 'Tắt' : 'Bật'}
          </button>
        </div>
      </td>
    `
    bodyEl.append(tr)
  }
  tableEl.hidden = false
  loadingEl.hidden = true
}

async function load() {
  rows = await api('/admin/discounts')
  render()
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  const id = form.discountId.value
  try {
    if (id) {
      await api(`/admin/discounts/${id}`, { method: 'PUT', body: JSON.stringify(payloadFromForm()) })
      flash('Đã cập nhật mã', false)
    } else {
      await api('/admin/discounts', { method: 'POST', body: JSON.stringify(payloadFromForm()) })
      flash('Đã tạo mã', false)
    }
    resetForm()
    await load()
  } catch (err) {
    flash(err.message || 'Lưu thất bại', true)
  }
})

resetBtn.addEventListener('click', resetForm)

bodyEl.addEventListener('click', async (event) => {
  const editId = event.target.getAttribute?.('data-edit')
  const toggleId = event.target.getAttribute?.('data-toggle')
  if (editId) {
    const d = rows.find((r) => String(r.id) === editId)
    if (!d) return
    form.discountId.value = d.id
    form.code.value = d.code
    form.description.value = d.description || ''
    form.type.value = d.type
    form.value.value = d.value
    form.minimumOrderAmount.value = d.minimumOrderAmount ?? ''
    form.maximumDiscountAmount.value = d.maximumDiscountAmount ?? ''
    form.usageLimit.value = d.usageLimit ?? ''
    form.startAt.value = toLocalInput(d.startAt)
    form.endAt.value = toLocalInput(d.endAt)
    formTitle.textContent = `Sửa ${d.code}`
    return
  }
  if (toggleId) {
    try {
      await api(`/admin/discounts/${toggleId}`, { method: 'DELETE' })
      flash('Đã đổi trạng thái mã', false)
      await load()
    } catch (err) {
      flash(err.message || 'Đổi trạng thái thất bại', true)
    }
  }
})

await requireAdmin()
await load()
