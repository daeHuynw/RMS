import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#table-table')
const bodyEl = document.querySelector('#table-body')
const form = document.querySelector('#table-form')
const formTitle = document.querySelector('#form-title')
const resetBtn = document.querySelector('#reset-btn')
const qrBox = document.querySelector('#qr-box')
const qrTokenEl = document.querySelector('#qr-token')
const qrLink = document.querySelector('#qr-link')
const rotateBtn = document.querySelector('#rotate-btn')
const copyBtn = document.querySelector('#copy-btn')

let tables = []
let selectedId = null

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function resetForm() {
  form.tableId.value = ''
  form.tableCode.value = ''
  form.capacity.value = 4
  form.area.value = ''
  formTitle.textContent = 'Thêm bàn'
  selectedId = null
  qrBox.hidden = true
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function render() {
  bodyEl.innerHTML = ''
  for (const table of tables) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td>${escapeHtml(table.tableCode)}</td>
      <td>${table.capacity}</td>
      <td>${escapeHtml(table.area || '')}</td>
      <td><span class="badge">${escapeHtml(table.status)}</span></td>
      <td>${table.hasQr ? 'Có' : '—'}</td>
      <td>
        <div class="admin-actions">
          <button type="button" class="btn-secondary" data-edit="${table.id}">Sửa</button>
          <button type="button" data-qr="${table.id}">QR</button>
        </div>
      </td>
    `
    bodyEl.append(tr)
  }
  loadingEl.hidden = true
  tableEl.hidden = false
}

async function load() {
  tables = await api('/admin/tables')
  render()
}

async function showQr(tableId) {
  selectedId = tableId
  const qr = await api(`/admin/tables/${tableId}/qr`)
  qrBox.hidden = false
  qrTokenEl.textContent = qr.token
  qrLink.href = qr.orderUrlPath
  qrLink.textContent = qr.orderUrlPath
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  const id = form.tableId.value
  const payload = {
    tableCode: form.tableCode.value.trim(),
    capacity: Number(form.capacity.value),
    area: form.area.value.trim() || null,
  }
  try {
    if (id) {
      await api(`/admin/tables/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
      flash('Đã cập nhật bàn', false)
    } else {
      await api('/admin/tables', { method: 'POST', body: JSON.stringify(payload) })
      flash('Đã thêm bàn (kèm QR)', false)
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
  const qrId = event.target.getAttribute?.('data-qr')
  if (editId) {
    const table = tables.find((t) => String(t.id) === editId)
    if (!table) return
    form.tableId.value = table.id
    form.tableCode.value = table.tableCode
    form.capacity.value = table.capacity
    form.area.value = table.area || ''
    formTitle.textContent = `Sửa #${table.id}`
    return
  }
  if (qrId) {
    try {
      await showQr(qrId)
      flash('Đã lấy QR', false)
    } catch (err) {
      // seed bàn có thể chưa có token nếu bootstrap chưa chạy — rotate
      try {
        const qr = await api(`/admin/tables/${qrId}/qr/rotate`, { method: 'POST', body: '{}' })
        selectedId = Number(qrId)
        qrBox.hidden = false
        qrTokenEl.textContent = qr.token
        qrLink.href = qr.orderUrlPath
        qrLink.textContent = qr.orderUrlPath
        flash('Đã tạo QR mới', false)
        await load()
      } catch (e2) {
        flash(e2.message || err.message || 'Không lấy được QR', true)
      }
    }
  }
})

rotateBtn.addEventListener('click', async () => {
  if (!selectedId) return
  try {
    const qr = await api(`/admin/tables/${selectedId}/qr/rotate`, { method: 'POST', body: '{}' })
    qrTokenEl.textContent = qr.token
    qrLink.href = qr.orderUrlPath
    qrLink.textContent = qr.orderUrlPath
    flash('Đã xoay QR — link cũ hết hiệu lực', false)
    await load()
  } catch (err) {
    flash(err.message || 'Xoay QR thất bại', true)
  }
})

copyBtn.addEventListener('click', async () => {
  const url = new URL(qrLink.getAttribute('href'), window.location.origin).toString()
  try {
    await navigator.clipboard.writeText(url)
    flash('Đã copy link QR', false)
  } catch {
    flash(url, false)
  }
})

await requireAdmin()
await load()
