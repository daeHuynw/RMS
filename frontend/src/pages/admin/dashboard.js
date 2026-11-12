import { api } from '../../api.js'
import { fetchMe } from '../../auth.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const form = document.querySelector('#range-form')
const overviewEl = document.querySelector('#overview')
const revenueChartEl = document.querySelector('#revenue-chart')
const revenueEl = document.querySelector('#revenue-list')
const orderEl = document.querySelector('#order-stats')
const topEl = document.querySelector('#top-list')
const peakChartEl = document.querySelector('#peak-chart')
const peakEl = document.querySelector('#peak-list')
const notifList = document.querySelector('#notif-list')
const notifUnread = document.querySelector('#notif-unread')

function showError(message) {
  errorEl.hidden = false
  errorEl.textContent = message
}

/** Chỉ về login khi session thật sự mất — không xóa token vì một API admin lỗi. */
async function explainLoadError(err) {
  if (err.status === 403) {
    showError('Tài khoản không có quyền ADMIN cho API này (403).')
    return
  }
  if (err.status === 401) {
    const me = await fetchMe()
    if (!me) {
      showError('Phiên đăng nhập hết hạn. Hãy đăng nhập lại.')
      window.setTimeout(() => {
        window.location.href = '/'
      }, 1200)
      return
    }
    showError(
      `API từ chối xác thực (401) dù vẫn login được. ${err.path || ''} ${err.message || ''}`.trim(),
    )
    return
  }
  showError(err.message || 'Tải dashboard thất bại')
}

function money(value) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value || 0))
}

function moneyShort(value) {
  const n = Number(value || 0)
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(n >= 10_000_000 ? 0 : 1)}tr`
  if (n >= 1_000) return `${Math.round(n / 1_000)}k`
  return String(Math.round(n))
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function localIsoDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function defaultRange() {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - 7)
  form.from.value = localIsoDate(from)
  form.to.value = localIsoDate(to)
}

function eachDay(fromIso, toIso) {
  const days = []
  const cursor = new Date(`${fromIso}T00:00:00`)
  const end = new Date(`${toIso}T00:00:00`)
  while (cursor <= end) {
    days.push(localIsoDate(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }
  return days
}

function shortDayLabel(iso) {
  const [, m, d] = iso.split('-')
  return `${Number(m)}/${Number(d)}`
}

/**
 * Pure SVG bar chart — no chart library.
 * @param {{ label: string, value: number, title?: string }[]} points
 */
function renderBarChart(container, points, { formatTip, barClass = 'dash-bar' } = {}) {
  if (!points.length) {
    container.innerHTML = '<p class="muted">Chưa có dữ liệu để vẽ biểu đồ.</p>'
    return
  }

  const max = Math.max(...points.map((p) => Number(p.value) || 0), 1)
  const W = 720
  const H = 220
  const pad = { t: 20, r: 12, b: 40, l: 52 }
  const innerW = W - pad.l - pad.r
  const innerH = H - pad.t - pad.b
  const gap = Math.min(8, innerW / points.length / 4)
  const barW = Math.max(4, (innerW - gap * (points.length - 1)) / points.length)
  const labelEvery = points.length > 16 ? Math.ceil(points.length / 12) : 1

  const gridYs = [0, 0.5, 1].map((t) => {
    const y = pad.t + innerH * (1 - t)
    const val = max * t
    return `<line class="dash-gridline" x1="${pad.l}" y1="${y}" x2="${W - pad.r}" y2="${y}" />
      <text class="dash-axis" x="${pad.l - 8}" y="${y + 4}" text-anchor="end">${escapeHtml(moneyShort(val))}</text>`
  })

  const bars = points
    .map((p, i) => {
      const v = Number(p.value) || 0
      const h = (v / max) * innerH
      const x = pad.l + i * (barW + gap)
      const y = pad.t + innerH - h
      const tip = formatTip ? formatTip(p) : `${p.label}: ${v}`
      const showLabel = i % labelEvery === 0 || i === points.length - 1
      const label = showLabel
        ? `<text class="dash-axis" x="${x + barW / 2}" y="${H - 12}" text-anchor="middle">${escapeHtml(p.label)}</text>`
        : ''
      return `<g>
        <rect class="${barClass}${v === max && v > 0 ? ' is-peak' : ''}" x="${x}" y="${y}" width="${barW}" height="${Math.max(h, v > 0 ? 2 : 0)}" rx="3">
          <title>${escapeHtml(tip)}</title>
        </rect>
        ${label}
      </g>`
    })
    .join('')

  container.innerHTML = `
    <svg class="dash-chart-svg" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
      ${gridYs.join('')}
      ${bars}
    </svg>
  `
}

async function loadDashboard() {
  const q = new URLSearchParams({ from: form.from.value, to: form.to.value })
  const [overview, revenue, orders, top, peak] = await Promise.all([
    api(`/admin/dashboard/overview?${q}`),
    api(`/admin/dashboard/revenue?${q}`),
    api(`/admin/dashboard/orders?${q}`),
    api(`/admin/dashboard/top-selling?${q}&limit=8`),
    api(`/admin/dashboard/peak-hours?${q}`),
  ])

  overviewEl.hidden = false
  overviewEl.innerHTML = `
    <div class="dash-card"><strong>${overview.orderCount}</strong><span>Đơn</span></div>
    <div class="dash-card"><strong>${overview.customerCount}</strong><span>Khách (đã login)</span></div>
    <div class="dash-card"><strong>${money(overview.revenue)}</strong><span>Doanh thu</span></div>
    <div class="dash-card"><strong>${overview.occupiedTables}/${overview.reservedTables}/${overview.availableTables}</strong><span>OCC/RES/AVL</span></div>
  `

  const byDay = new Map((revenue.daily || []).map((r) => [String(r.day).slice(0, 10), Number(r.revenue) || 0]))
  const revenuePoints = eachDay(form.from.value, form.to.value).map((day) => ({
    label: shortDayLabel(day),
    value: byDay.get(day) || 0,
    title: day,
  }))
  renderBarChart(revenueChartEl, revenuePoints, {
    formatTip: (p) => `${p.title}: ${money(p.value)}`,
    barClass: 'dash-bar dash-bar-revenue',
  })

  revenueEl.innerHTML = (revenue.daily || []).length
    ? `<ul>${revenue.daily.map((r) => `<li>${escapeHtml(String(r.day).slice(0, 10))} · ${money(r.revenue)}</li>`).join('')}</ul>
       <p><strong>Tổng: ${money(revenue.total)}</strong></p>`
    : '<p class="muted">Chưa có thanh toán SUCCESS trong khoảng.</p>'

  orderEl.innerHTML = `
    <p>Tổng ${orders.total} · Hủy ${orders.cancelled} · tỉ lệ ${(Number(orders.cancellationRate) * 100).toFixed(1)}%</p>
    <ul>${(orders.byStatus || []).map((s) => `<li>${escapeHtml(s.status)} · ${s.total}</li>`).join('')}</ul>
  `

  topEl.innerHTML = (top || []).length
    ? `<ul>${top.map((t) => `<li>${escapeHtml(t.itemName)} × ${t.quantitySold} · ${money(t.revenue)}</li>`).join('')}</ul>`
    : '<p class="muted">Chưa có món COMPLETED.</p>'

  const byHour = new Map((peak || []).map((p) => [Number(p.hour), Number(p.total) || 0]))
  const peakPoints = Array.from({ length: 24 }, (_, hour) => ({
    label: String(hour).padStart(2, '0'),
    value: byHour.get(hour) || 0,
  }))
  renderBarChart(peakChartEl, peakPoints, {
    formatTip: (p) => `${p.label}:00 · ${p.value} đơn`,
    barClass: 'dash-bar dash-bar-peak',
  })

  const peakSorted = [...(peak || [])].sort((a, b) => Number(b.total) - Number(a.total)).slice(0, 5)
  peakEl.innerHTML = peakSorted.length
    ? `<p class="muted">Top giờ:</p><ul>${peakSorted
        .map((p) => `<li>${String(p.hour).padStart(2, '0')}:00 · ${p.total} đơn</li>`)
        .join('')}</ul>`
    : '<p class="muted">Chưa có dữ liệu giờ.</p>'
}

async function loadNotifications() {
  const [count, rows] = await Promise.all([
    api('/notifications/unread-count'),
    api('/notifications'),
  ])
  notifUnread.textContent = `Chưa đọc: ${count.unread}`
  notifList.innerHTML = rows.length
    ? `<ul>${rows.map((n) => `<li class="${n.read ? 'muted' : ''}"><strong>${escapeHtml(n.title)}</strong> · ${escapeHtml(n.content || '')}</li>`).join('')}</ul>`
    : '<p class="muted">Không có thông báo.</p>'
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  errorEl.hidden = true
  try {
    await loadDashboard()
  } catch (err) {
    await explainLoadError(err)
  }
})

document.querySelector('#notif-read-all').addEventListener('click', async () => {
  try {
    await api('/notifications/read-all', { method: 'PATCH' })
    await loadNotifications()
  } catch (err) {
    await explainLoadError(err)
  }
})

await requireAdmin()
defaultRange()
try {
  await loadDashboard()
  await loadNotifications()
} catch (err) {
  await explainLoadError(err)
}
