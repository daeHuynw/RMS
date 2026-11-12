import { api } from '../../api.js'
import { requireCustomer } from '../auth/requireCustomer.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const loadingEl = document.querySelector('#loading')
const listEl = document.querySelector('#order-list')

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

await requireCustomer()

try {
  const rows = await api('/orders/my')
  loadingEl.hidden = true
  if (!rows.length) {
    listEl.innerHTML = '<p class="muted">Chưa có đơn nào. Đăng nhập rồi gọi món qua QR để gắn đơn vào tài khoản.</p>'
  } else {
    for (const order of rows) {
      const card = document.createElement('article')
      card.className = 'order-card'
      const lines = (order.items || [])
        .map(
          (i) =>
            `<li>${escapeHtml(i.itemName)} × ${i.quantity} · ${money(i.unitPrice)}</li>`,
        )
        .join('')
      card.innerHTML = `
        <div class="order-card-head">
          <strong>${escapeHtml(order.orderNumber)}</strong>
          <span class="badge">${escapeHtml(order.status)}</span>
        </div>
        <p class="muted">${escapeHtml(order.tableCode || '—')} · ${fmt(order.createdAt)} · ${money(order.totalAmount)}</p>
        <ul class="order-lines">${lines}</ul>
      `
      listEl.append(card)
    }
  }
} catch (err) {
  loadingEl.hidden = true
  errorEl.hidden = false
  errorEl.textContent = err.message || 'Không tải được đơn'
}
