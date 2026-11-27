import { mountAppNav } from '../nav.js'
import { hasAnyRole } from '../auth.js'
import { escapeHtml } from '../utils.js'
import { splitText, reveal } from '../effects.js'

const titleEl = document.querySelector('#home-title')
const subEl = document.querySelector('#home-sub')
const gridEl = document.querySelector('#home-grid')
const ctaEl = document.querySelector('#home-cta')

const CARDS = [
  { href: '/menu.html', title: 'Thực đơn', desc: 'Xem món · gọi món sau check-in', roles: null },
  { href: '/reservations.html', title: 'Đặt bàn', desc: 'Đăng nhập → đặt → chờ check-in', roles: ['CUSTOMER', 'ADMIN'] },
  { href: '/my-orders.html', title: 'Đơn của tôi', desc: 'Theo dõi đơn gắn tài khoản', roles: ['CUSTOMER', 'ADMIN'] },
  { href: '/staff/tables.html', title: 'Sơ đồ bàn', desc: 'Mở/đóng phiên · trạng thái', roles: ['STAFF', 'ADMIN'] },
  { href: '/staff/orders.html', title: 'Đơn & thanh toán', desc: 'Tạo đơn · thu tiền · hóa đơn', roles: ['STAFF', 'ADMIN'] },
  { href: '/staff/reservations.html', title: 'Lịch đặt bàn', desc: 'Xác nhận · check-in', roles: ['STAFF', 'ADMIN'] },
  { href: '/kitchen/', title: 'Màn hình bếp', desc: 'Ticket realtime STOMP', roles: ['KITCHEN', 'STAFF', 'ADMIN'] },
  { href: '/admin/dashboard.html', title: 'Admin dashboard', desc: 'Báo cáo · users · settings', roles: ['ADMIN'] },
  { href: '/profile.html', title: 'Hồ sơ', desc: 'Đổi thông tin / mật khẩu', roles: ['CUSTOMER', 'STAFF', 'KITCHEN', 'ADMIN'] },
  { href: '/', title: 'Đăng nhập', desc: 'Vào hệ thống bằng email', roles: 'guest-only' },
  { href: '/register.html', title: 'Đăng ký', desc: 'Tạo tài khoản CUSTOMER', roles: 'guest-only' },
]

function visibleCards(me) {
  return CARDS.filter((card) => {
    if (card.roles === 'guest-only') return me == null
    if (card.roles == null) return true
    return me != null && hasAnyRole(me, card.roles)
  })
}

const swapBtn = (href, label) =>
  `<a class="btn-swap" href="${href}" data-text="${escapeHtml(label)}"><span>${escapeHtml(label)}</span></a>`
const ghostBtn = (href, label) =>
  `<a class="btn-ghost" href="${href}">${escapeHtml(label)}</a>`

const me = await mountAppNav()
const isOps = me != null && hasAnyRole(me, ['STAFF', 'KITCHEN', 'ADMIN']) && !hasAnyRole(me, ['CUSTOMER'])

if (me) {
  titleEl.textContent = `Xin chào, ${me.fullName}`
  subEl.textContent = `Vai trò: ${me.roles.join(', ')}`
} else {
  titleEl.textContent = 'Mỗi món ăn là một tác phẩm'
  subEl.textContent = 'Xem thực đơn · đăng nhập để đặt bàn · gọi món sau khi được check-in tại bàn'
}

if (isOps) {
  document.querySelector('.home').classList.add('home-ops')
  ctaEl.innerHTML = ghostBtn('/staff/tables.html', 'Vào ca làm') + ghostBtn('/menu.html', 'Thực đơn')
} else if (me) {
  ctaEl.innerHTML = swapBtn('/reservations.html', 'Đặt bàn') + ghostBtn('/my-orders.html', 'Đơn của tôi')
} else {
  ctaEl.innerHTML = swapBtn('/menu.html', 'Xem thực đơn') + ghostBtn('/', 'Đăng nhập')
}

gridEl.innerHTML = visibleCards(me)
  .map((card, i) => `
    <a class="home-card${isOps ? '' : ' rise'}" style="--d:${i % 6}" href="${card.href}">
      <strong>${escapeHtml(card.title)}</strong>
      <span class="muted">${escapeHtml(card.desc)}</span>
    </a>
  `)
  .join('')

if (!isOps) splitText(titleEl)
reveal()