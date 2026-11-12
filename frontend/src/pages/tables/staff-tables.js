import { api } from '../../api.js'
import { requireStaff } from '../staff/requireStaff.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const emptyEl = document.querySelector('#empty')
const gridEl = document.querySelector('#table-grid')
const areaFilterEl = document.querySelector('#area-filter')

const STATUS_LABEL = {
  AVAILABLE: 'Bàn trống',
  RESERVED: 'Đã giữ chỗ',
  OCCUPIED: 'Đang ngồi',
  OUT_OF_SERVICE: 'Ngưng dùng',
}

let tables = []
let openSessions = []
let selectedArea = ''
let tickTimer = null

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function sessionFor(tableId) {
  return openSessions.find((s) => s.tableId === tableId)
}

function formatElapsed(openedAt) {
  if (!openedAt) return '00:00:00'
  const start = Date.parse(openedAt)
  if (Number.isNaN(start)) return '00:00:00'
  const totalSec = Math.max(0, Math.floor((Date.now() - start) / 1000))
  const h = String(Math.floor(totalSec / 3600)).padStart(2, '0')
  const m = String(Math.floor((totalSec % 3600) / 60)).padStart(2, '0')
  const s = String(totalSec % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}

function areasFromTables() {
  const set = new Set()
  for (const table of tables) {
    const area = (table.area || '').trim()
    if (area) set.add(area)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'vi'))
}

function syncAreaOptions() {
  const areas = areasFromTables()
  const previous = selectedArea
  areaFilterEl.innerHTML = '<option value="">Tất cả</option>'
  for (const area of areas) {
    const opt = document.createElement('option')
    opt.value = area
    opt.textContent = area
    areaFilterEl.append(opt)
  }
  selectedArea = previous && areas.includes(previous) ? previous : ''
  areaFilterEl.value = selectedArea
}

function visibleTables() {
  if (!selectedArea) return tables
  return tables.filter((t) => (t.area || '').trim() === selectedArea)
}

function render() {
  const list = visibleTables()
  gridEl.innerHTML = ''

  for (const table of list) {
    const session = sessionFor(table.id)
    const occupied = Boolean(session) || table.status === 'OCCUPIED'
    const statusLabel = STATUS_LABEL[table.status] || table.status
    const card = document.createElement('article')
    card.className = `table-card status-${table.status}${occupied ? ' is-occupied' : ''}`
    card.dataset.tableId = String(table.id)

    const timerHtml = session
      ? `<p class="table-timer" data-opened-at="${escapeHtml(session.openedAt)}">${formatElapsed(session.openedAt)}</p>`
      : `<p class="table-status-line">${escapeHtml(statusLabel)}</p>`

    card.innerHTML = `
      <h2>${escapeHtml(table.tableCode)}</h2>
      <p class="muted">${table.capacity} ghế · ${escapeHtml(table.area || '—')}</p>
      ${timerHtml}
      <p class="muted table-session">${session ? `Phiên #${session.id}` : statusLabel}</p>
      <div class="table-actions">
        ${
          session
            ? `<button type="button" data-close="${session.id}">Đóng phiên</button>`
            : `<button type="button" data-open="${table.id}">Mở phiên</button>`
        }
        <button type="button" class="btn-secondary" data-status="${table.id}" data-next="AVAILABLE">Trống</button>
        <button type="button" class="btn-secondary" data-status="${table.id}" data-next="OUT_OF_SERVICE">Ngưng</button>
      </div>
    `
    gridEl.append(card)
  }

  loadingEl.hidden = true
  emptyEl.hidden = list.length > 0
  gridEl.hidden = list.length === 0
  tickTimers()
}

function tickTimers() {
  gridEl.querySelectorAll('.table-timer[data-opened-at]').forEach((el) => {
    el.textContent = formatElapsed(el.getAttribute('data-opened-at'))
  })
}

function startTicker() {
  if (tickTimer) clearInterval(tickTimer)
  tickTimer = setInterval(tickTimers, 1000)
}

async function load() {
  ;[tables, openSessions] = await Promise.all([
    api('/staff/tables'),
    api('/staff/sessions/open'),
  ])
  syncAreaOptions()
  render()
  startTicker()
}

areaFilterEl.addEventListener('change', () => {
  selectedArea = areaFilterEl.value
  render()
})

gridEl.addEventListener('click', async (event) => {
  const btn = event.target.closest?.('button')
  if (!btn) return
  const openId = btn.getAttribute('data-open')
  const closeId = btn.getAttribute('data-close')
  const statusId = btn.getAttribute('data-status')
  const next = btn.getAttribute('data-next')
  try {
    if (openId) {
      await api(`/staff/tables/${openId}/sessions`, { method: 'POST', body: '{}' })
      flash('Đã mở phiên', false)
      await load()
      return
    }
    if (closeId) {
      await api(`/staff/sessions/${closeId}/close`, { method: 'POST', body: '{}' })
      flash('Đã đóng phiên', false)
      await load()
      return
    }
    if (statusId && next) {
      await api(`/staff/tables/${statusId}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: next }),
      })
      flash(`Đã đổi sang ${STATUS_LABEL[next] || next}`, false)
      await load()
    }
  } catch (err) {
    flash(err.message || 'Thao tác thất bại', true)
  }
})

await requireStaff()
await load()
