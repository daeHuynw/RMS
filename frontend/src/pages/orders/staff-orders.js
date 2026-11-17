import { api, apiBlob } from '../../api.js'
import { requireStaff } from '../staff/requireStaff.js'
import { mountAppNav } from '../../nav.js'
import { escapeHtml, money, formatDateTime, sendErrorText, sendIdempotent } from '../../utils.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const listEl = document.querySelector('#order-list')
const form = document.querySelector('#create-form')
const sessionSelect = document.querySelector('#sessionId')
const itemRowsEl = document.querySelector('#item-rows')
const addRowBtn = document.querySelector('#add-item-row')

/** Món đang bán, nạp một lần từ /menu cho các ô chọn món. */
let menuItems = []

const PAY_METHODS = {
  CASH: 'Tiền mặt',
  BANK_TRANSFER: 'Chuyển khoản',
}

// Staff chỉ bấm hai bước: xác nhận đơn và mang ra. IN_PROGRESS / READY do bếp quyết định
// (bếp bắt đầu món đầu tiên → IN_PROGRESS, mọi món xong → READY); backend từ chối nếu gửi tay.
const NEXT = {
  PENDING: 'CONFIRMED',
  READY: 'SERVED',
}

const WAITING_FOR_KITCHEN = {
  CONFIRMED: 'Đã gửi bếp — chờ bếp bắt đầu',
  IN_PROGRESS: 'Bếp đang làm — đơn tự sang READY khi mọi món xong',
}

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

async function loadSessionsAndMenu() {
  const [sessions, catalog] = await Promise.all([
    api('/staff/sessions/open'),
    api('/menu'),
  ])
  sessionSelect.innerHTML = ''
  if (!sessions.length) {
    sessionSelect.innerHTML = '<option value="">Không có phiên OPEN</option>'
  } else {
    for (const s of sessions) {
      const opt = document.createElement('option')
      opt.value = s.id
      opt.textContent = `${s.tableCode} · session #${s.id}`
      sessionSelect.append(opt)
    }
  }
  menuItems = catalog.items || []
  resetItemRows()
}

// Mỗi dòng: chọn món + số lượng + nút xoá. Luôn còn ít nhất một dòng.
function addItemRow() {
  const row = document.createElement('div')
  row.className = 'item-row'
  const options = menuItems
    .map((item) => `<option value="${item.id}">${escapeHtml(item.name)} · ${money(item.price)}</option>`)
    .join('')
  row.innerHTML = `
    <select data-item-select required aria-label="Món">${options}</select>
    <input data-item-qty type="number" min="1" max="99" value="1" required aria-label="Số lượng" />
    <button type="button" class="btn-secondary" data-remove-row>Xoá</button>
  `
  itemRowsEl.append(row)
}

function resetItemRows() {
  itemRowsEl.innerHTML = ''
  addItemRow()
}

/** Gộp các dòng trùng món thành một (bếp nhận một dòng "Pepsi × 3" thay vì ba dòng). */
function collectItems() {
  const byId = new Map()
  for (const row of itemRowsEl.querySelectorAll('.item-row')) {
    const id = Number(row.querySelector('[data-item-select]').value)
    const qty = Number(row.querySelector('[data-item-qty]').value)
    if (!id || !qty) continue
    byId.set(id, (byId.get(id) || 0) + qty)
  }
  return [...byId].map(([menuItemId, quantity]) => ({ menuItemId, quantity }))
}

addRowBtn.addEventListener('click', addItemRow)

itemRowsEl.addEventListener('click', (event) => {
  const btn = event.target.closest?.('[data-remove-row]')
  if (!btn) return
  btn.closest('.item-row').remove()
  if (!itemRowsEl.querySelector('.item-row')) addItemRow()
})

function payBox(order) {
  const options = Object.entries(PAY_METHODS)
    .map(([value, label]) => `<option value="${value}">${label}</option>`)
    .join('')
  return `
    <span class="pay-box">
      <select data-pay-method="${order.id}" aria-label="Cách thu">${options}</select>
      <input data-pay-ref="${order.id}" maxlength="120" placeholder="Mã giao dịch (tuỳ chọn)" hidden />
      <button type="button" data-pay="${order.id}" data-amount="${order.totalAmount}">Thu ${money(order.totalAmount)}</button>
    </span>
  `
}

async function loadOrders() {
  const rows = await api('/staff/orders')
  listEl.innerHTML = ''
  loadingEl.hidden = true
  if (!rows.length) {
    listEl.innerHTML = '<p class="muted">Chưa có đơn.</p>'
    return
  }
  for (const order of rows) {
    const card = document.createElement('article')
    card.className = 'order-card'
    const next = NEXT[order.status]
    const canCancel = order.status === 'PENDING' || order.status === 'CONFIRMED'
    const canPay = order.status === 'SERVED'
    const canInvoice = order.status === 'COMPLETED'
    const lines = (order.items || [])
      .map((i) => `<li>${escapeHtml(i.itemName)} × ${i.quantity}</li>`)
      .join('')
    card.innerHTML = `
      <div class="order-card-head">
        <strong>${escapeHtml(order.orderNumber)}</strong>
        <span class="badge">${escapeHtml(order.status)}</span>
      </div>
      <p class="muted">${escapeHtml(order.tableCode || '—')} · ${formatDateTime(order.createdAt)} · ${money(order.totalAmount)}</p>
      <ul class="order-lines">${lines}</ul>
      <div class="admin-actions">
        ${next ? `<button type="button" data-status="${order.id}" data-next="${next}">→ ${next}</button>` : ''}
        ${WAITING_FOR_KITCHEN[order.status] ? `<span class="muted">${WAITING_FOR_KITCHEN[order.status]}</span>` : ''}
        ${canPay ? payBox(order) : ''}
        ${canInvoice ? `<button type="button" class="btn-secondary" data-invoice="${order.id}">Hóa đơn PDF</button>` : ''}
        ${canCancel ? `<button type="button" class="btn-danger" data-cancel="${order.id}">Hủy</button>` : ''}
      </div>
    `
    listEl.append(card)
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  const items = collectItems()
  if (!items.length) {
    flash('Chọn ít nhất một món', true)
    return
  }
  const submitBtn = form.querySelector('button[type="submit"]')
  submitBtn.disabled = true
  const body = JSON.stringify({
    tableSessionId: Number(form.sessionId.value),
    items,
    customerNote: form.customerNote.value.trim() || null,
    discountCode: form.discountCode.value.trim() || null,
  })
  try {
    const order = await sendIdempotent('staff-create', body, (key) =>
      api('/staff/orders', { method: 'POST', headers: { 'Idempotency-Key': key }, body }),
    )
    flash(`Đã tạo ${order.orderNumber} · ${money(order.totalAmount)}`, false)
    resetItemRows()
    form.customerNote.value = ''
    form.discountCode.value = ''
    await loadOrders()
  } catch (err) {
    flash(sendErrorText(err, 'Tạo đơn thất bại'), true)
  } finally {
    submitBtn.disabled = false
  }
})

// Chọn "Chuyển khoản" mới hiện ô mã giao dịch.
listEl.addEventListener('change', (event) => {
  const orderId = event.target.getAttribute?.('data-pay-method')
  if (!orderId) return
  const refInput = listEl.querySelector(`[data-pay-ref="${orderId}"]`)
  refInput.hidden = event.target.value !== 'BANK_TRANSFER'
})

listEl.addEventListener('click', async (event) => {
  const statusId = event.target.getAttribute?.('data-status')
  const next = event.target.getAttribute?.('data-next')
  const cancelId = event.target.getAttribute?.('data-cancel')
  const payId = event.target.getAttribute?.('data-pay')
  const amount = event.target.getAttribute?.('data-amount')
  const invoiceOrderId = event.target.getAttribute?.('data-invoice')
  try {
    if (statusId && next) {
      await api(`/staff/orders/${statusId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: next }),
      })
      flash(`Đã chuyển → ${next}`, false)
      await loadOrders()
      return
    }
    if (cancelId) {
      await api(`/staff/orders/${cancelId}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ reason: 'Cancelled by staff' }),
      })
      flash('Đã hủy đơn', false)
      await loadOrders()
      return
    }
    if (payId) {
      const method = listEl.querySelector(`[data-pay-method="${payId}"]`).value
      const reference = method === 'BANK_TRANSFER'
        ? listEl.querySelector(`[data-pay-ref="${payId}"]`).value.trim() || null
        : null
      const body = JSON.stringify({ amount: Number(amount), method, reference })
      event.target.disabled = true
      try {
        const payment = await sendIdempotent(`pay:${payId}`, body, (key) =>
          api(`/staff/orders/${payId}/payments`, {
            method: 'POST',
            headers: { 'Idempotency-Key': key },
            body,
          }),
        )
        flash(`Đã thu ${money(payment.amount)} (${PAY_METHODS[method]}) · HĐ ${payment.invoiceNumber}`, false)
        await loadOrders()
      } finally {
        event.target.disabled = false
      }
      return
    }
    if (invoiceOrderId) {
      const invoice = await api(`/invoices/by-order/${invoiceOrderId}`)
      const blob = await apiBlob(`/invoices/${invoice.id}/pdf`)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${invoice.invoiceNumber}.pdf`
      a.click()
      URL.revokeObjectURL(url)
      flash(`Đã tải ${invoice.invoiceNumber}.pdf`, false)
    }
  } catch (err) {
    flash(sendErrorText(err, 'Thao tác thất bại'), true)
  }
})

await requireStaff()
await loadSessionsAndMenu()
await loadOrders()
