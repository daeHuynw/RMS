import { api, apiBlob } from '../../api.js'
import { requireStaff } from '../staff/requireStaff.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const listEl = document.querySelector('#order-list')
const form = document.querySelector('#create-form')
const sessionSelect = document.querySelector('#sessionId')
const menuSelect = document.querySelector('#menuItemId')

const NEXT = {
  PENDING: 'CONFIRMED',
  CONFIRMED: 'IN_PROGRESS',
  IN_PROGRESS: 'READY',
  READY: 'SERVED',
}

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function money(value) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value))
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function fmt(iso) {
  return iso ? new Date(iso).toLocaleString('vi-VN') : ''
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
  menuSelect.innerHTML = ''
  for (const item of catalog.items || []) {
    const opt = document.createElement('option')
    opt.value = item.id
    opt.textContent = `${item.name} · ${money(item.price)}`
    menuSelect.append(opt)
  }
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
      <p class="muted">${escapeHtml(order.tableCode || '—')} · ${fmt(order.createdAt)} · ${money(order.totalAmount)}</p>
      <ul class="order-lines">${lines}</ul>
      <div class="admin-actions">
        ${next ? `<button type="button" data-status="${order.id}" data-next="${next}">→ ${next}</button>` : ''}
        ${canPay ? `<button type="button" data-pay="${order.id}" data-amount="${order.totalAmount}">Thanh toán tiền mặt</button>` : ''}
        ${canInvoice ? `<button type="button" class="btn-secondary" data-invoice="${order.id}">Hóa đơn PDF</button>` : ''}
        ${canCancel ? `<button type="button" class="btn-danger" data-cancel="${order.id}">Hủy</button>` : ''}
      </div>
    `
    listEl.append(card)
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  try {
    await api('/staff/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify({
        tableSessionId: Number(form.sessionId.value),
        items: [{ menuItemId: Number(form.menuItemId.value), quantity: Number(form.quantity.value) }],
        customerNote: form.customerNote.value.trim() || null,
        discountCode: form.discountCode.value.trim() || null,
      }),
    })
    flash('Đã tạo đơn', false)
    form.quantity.value = '1'
    form.customerNote.value = ''
    form.discountCode.value = ''
    await loadOrders()
  } catch (err) {
    flash(err.message || 'Tạo đơn thất bại', true)
  }
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
      const payment = await api(`/staff/orders/${payId}/payments`, {
        method: 'POST',
        headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({
          amount: Number(amount),
          method: 'CASH',
          reference: null,
        }),
      })
      flash(`Đã thu ${money(payment.amount)} · HĐ ${payment.invoiceNumber}`, false)
      await loadOrders()
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
    flash(err.message || 'Thao tác thất bại', true)
  }
})

await requireStaff()
await loadSessionsAndMenu()
await loadOrders()
