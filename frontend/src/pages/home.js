import { mountAppNav } from '../nav.js'
import { hasAnyRole } from '../auth.js'

const titleEl = document.querySelector('#home-title')
const subEl = document.querySelector('#home-sub')
const gridEl = document.querySelector('#home-grid')

const CARDS = [
  {
    href: '/menu.html',
    title: 'Thực đơn',
    desc: 'Xem món · gọi món sau check-in',
    roles: null,
  },
  {
    href: '/reservations.html',
    title: 'Đặt bàn',
    desc: 'Đăng nhập → đặt → chờ check-in',
    roles: ['CUSTOMER', 'ADMIN'],
  },
  {
    href: '/my-orders.html',
    title: 'Đơn của tôi',
    desc: 'Theo dõi đơn gắn tài khoản',
    roles: ['CUSTOMER', 'ADMIN'],
  },
  {
    href: '/staff/tables.html',
    title: 'Sơ đồ bàn',
    desc: 'Mở/đóng phiên · trạng thái',
    roles: ['STAFF', 'ADMIN'],
  },
  {
    href: '/staff/orders.html',
    title: 'Đơn & thanh toán',
    desc: 'Tạo đơn · thu tiền · hóa đơn',
    roles: ['STAFF', 'ADMIN'],
  },
  {
    href: '/staff/reservations.html',
    title: 'Lịch đặt bàn',
    desc: 'Xác nhận · check-in',
    roles: ['STAFF', 'ADMIN'],
  },
  {
    href: '/kitchen/',
    title: 'Màn hình bếp',
    desc: 'Ticket realtime STOMP',
    roles: ['KITCHEN', 'STAFF', 'ADMIN'],
  },
  {
    href: '/admin/dashboard.html',
    title: 'Admin dashboard',
    desc: 'Báo cáo · users · settings',
    roles: ['ADMIN'],
  },
  {
    href: '/profile.html',
    title: 'Hồ sơ',
    desc: 'Đổi thông tin / mật khẩu',
    roles: ['CUSTOMER', 'STAFF', 'KITCHEN', 'ADMIN'],
  },
  {
    href: '/',
    title: 'Đăng nhập',
    desc: 'Vào hệ thống bằng email',
    roles: 'guest-only',
  },
  {
    href: '/register.html',
    title: 'Đăng ký',
    desc: 'Tạo tài khoản CUSTOMER',
    roles: 'guest-only',
  },
]

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

function visibleCards(me) {
  return CARDS.filter((card) => {
    if (card.roles === 'guest-only') {
      return me == null
    }
    if (card.roles == null) {
      return true
    }
    return me != null && hasAnyRole(me, card.roles)
  })
}

const me = await mountAppNav()

if (me) {
  titleEl.textContent = `Xin chào, ${me.fullName}`
  subEl.textContent = `Vai trò: ${me.roles.join(', ')}`
} else {
  titleEl.textContent = 'Chào mừng đến RMS'
  subEl.textContent = 'Xem thực đơn · đăng nhập để đặt bàn rồi gọi món sau check-in'
}

gridEl.innerHTML = visibleCards(me)
  .map(
    (card) => `
    <a class="home-card" href="${card.href}">
      <strong>${escapeHtml(card.title)}</strong>
      <span class="muted">${escapeHtml(card.desc)}</span>
    </a>
  `,
  )
  .join('')
