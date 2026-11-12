import { Client } from '@stomp/stompjs'
import { api, accessToken } from '../../api.js'
import { requireKitchen } from './requireKitchen.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const gridEl = document.querySelector('#ticket-grid')
const wsStatusEl = document.querySelector('#ws-status')

const NEXT = {
  PENDING: 'PREPARING',
  PREPARING: 'READY',
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function fmt(iso) {
  return iso ? new Date(iso).toLocaleTimeString('vi-VN') : ''
}

function showError(message) {
  errorEl.hidden = false
  errorEl.textContent = message
}

function render(tickets) {
  gridEl.innerHTML = ''
  if (!tickets.length) {
    gridEl.innerHTML = '<p class="muted">Không có ticket đang mở.</p>'
    return
  }
  for (const ticket of tickets) {
    const card = document.createElement('article')
    card.className = 'ticket-card'
    const items = (ticket.items || [])
      .map((item) => {
        const next = NEXT[item.status]
        return `
          <li class="ticket-item status-${escapeHtml(item.status)}">
            <div>
              <strong>${escapeHtml(item.name)}</strong> × ${item.quantity}
              ${item.note ? `<p class="muted">${escapeHtml(item.note)}</p>` : ''}
              <span class="badge">${escapeHtml(item.status)}</span>
            </div>
            ${
              next
                ? `<button type="button" data-item="${item.id}" data-next="${next}">→ ${next}</button>`
                : '<span class="muted">xong</span>'
            }
          </li>
        `
      })
      .join('')
    card.innerHTML = `
      <header>
        <strong>${escapeHtml(ticket.orderNumber)}</strong>
        <span class="muted">${escapeHtml(ticket.tableCode || '—')} · ${fmt(ticket.createdAt)}</span>
      </header>
      <ul>${items}</ul>
    `
    gridEl.append(card)
  }
}

async function loadQueue() {
  const tickets = await api('/kitchen/orders')
  render(tickets)
}

gridEl.addEventListener('click', async (event) => {
  const itemId = event.target.getAttribute?.('data-item')
  const next = event.target.getAttribute?.('data-next')
  if (!itemId || !next) return
  try {
    await api(`/kitchen/order-items/${itemId}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status: next }),
    })
    await loadQueue()
  } catch (err) {
    showError(err.message || 'Cập nhật món thất bại')
  }
})

function connectRealtime() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const client = new Client({
    brokerURL: `${protocol}//${window.location.host}/ws`,
    connectHeaders: {
      Authorization: `Bearer ${accessToken()}`,
    },
    reconnectDelay: 3000,
    onConnect: () => {
      wsStatusEl.textContent = 'Realtime: đã kết nối (/topic/kitchen/orders)'
      client.subscribe('/topic/kitchen/orders', () => {
        void loadQueue().catch((err) => showError(err.message || 'Reload thất bại'))
      })
    },
    onStompError: () => {
      wsStatusEl.textContent = 'Realtime lỗi — đang thử lại…'
    },
    onWebSocketClose: () => {
      wsStatusEl.textContent = 'Realtime mất kết nối — đang thử lại…'
    },
  })
  client.activate()
}

await requireKitchen()
try {
  await loadQueue()
  connectRealtime()
} catch (err) {
  showError(err.message || 'Không tải được hàng đợi bếp')
}
