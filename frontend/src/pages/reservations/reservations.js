import { api } from '../../api.js'
import { requireCustomer } from '../auth/requireCustomer.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const searchForm = document.querySelector('#search-form')
const availabilityEl = document.querySelector('#availability')
const mineList = document.querySelector('#mine-list')
const loadingEl = document.querySelector('#loading')

let me = null

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function toIso(localValue) {
  // datetime-local → treat as local wall time
  return new Date(localValue).toISOString()
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

async function loadMine() {
  const rows = await api('/reservations/my')
  loadingEl.hidden = true
  mineList.innerHTML = ''
  if (!rows.length) {
    mineList.innerHTML = '<p class="muted">Chưa có đặt bàn.</p>'
    return
  }
  for (const r of rows) {
    const card = document.createElement('article')
    card.className = 'res-card'
    const canCancel = r.status === 'PENDING' || r.status === 'CONFIRMED'
    const canOrder = r.status === 'CHECKED_IN'
    card.innerHTML = `
      <strong>${escapeHtml(r.tableCode)}</strong>
      <span class="badge">${escapeHtml(r.status)}</span>
      <p class="muted">${fmt(r.start)} → ${fmt(r.end)} · ${r.guestCount} khách</p>
      ${r.specialRequest ? `<p class="muted">${escapeHtml(r.specialRequest)}</p>` : ''}
      <div class="admin-actions">
        ${canOrder ? `<a class="menu-link" href="/menu.html">Gọi món</a>` : ''}
        ${canCancel ? `<button type="button" class="btn-danger" data-cancel="${r.id}">Hủy</button>` : ''}
      </div>
    `
    mineList.append(card)
  }
}

searchForm.addEventListener('submit', async (event) => {
  event.preventDefault()
  const start = toIso(searchForm.start.value)
  const end = toIso(searchForm.end.value)
  const guestCount = Number(searchForm.guestCount.value)
  try {
    const q = new URLSearchParams({ start, end, guestCount: String(guestCount) })
    const tables = await api(`/tables/availability?${q}`)
    availabilityEl.hidden = false
    availabilityEl.innerHTML = ''
    if (!tables.length) {
      availabilityEl.innerHTML = '<p class="muted">Không còn bàn phù hợp.</p>'
      return
    }
    for (const t of tables) {
      const row = document.createElement('div')
      row.className = 'avail-row'
      row.innerHTML = `
        <div>
          <strong>${escapeHtml(t.tableCode)}</strong>
          <span class="muted"> · ${t.capacity} ghế · ${escapeHtml(t.area || '')}</span>
        </div>
        <button type="button" data-book="${t.tableId}">Đặt</button>
      `
      availabilityEl.append(row)
    }
    availabilityEl.dataset.start = start
    availabilityEl.dataset.end = end
    availabilityEl.dataset.guestCount = String(guestCount)
    availabilityEl.dataset.note = searchForm.specialRequest.value.trim()
  } catch (err) {
    flash(err.message || 'Không tìm được bàn', true)
  }
})

availabilityEl.addEventListener('click', async (event) => {
  const tableId = event.target.getAttribute?.('data-book')
  if (!tableId) return
  try {
    await api('/reservations', {
      method: 'POST',
      body: JSON.stringify({
        tableId: Number(tableId),
        start: availabilityEl.dataset.start,
        end: availabilityEl.dataset.end,
        guestCount: Number(availabilityEl.dataset.guestCount),
        specialRequest: availabilityEl.dataset.note || null,
      }),
    })
    flash('Đã gửi yêu cầu đặt bàn (PENDING)', false)
    await loadMine()
  } catch (err) {
    flash(err.message || 'Đặt bàn thất bại', true)
  }
})

mineList.addEventListener('click', async (event) => {
  const id = event.target.getAttribute?.('data-cancel')
  if (!id) return
  try {
    await api(`/reservations/${id}/cancel`, { method: 'POST', body: '{}' })
    flash('Đã hủy đặt bàn', false)
    await loadMine()
  } catch (err) {
    flash(err.message || 'Hủy thất bại', true)
  }
})

me = await requireCustomer()
await loadMine()
