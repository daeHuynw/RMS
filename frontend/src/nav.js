import { fetchMe, hasAnyRole, logoutAndRedirect } from './auth.js'
import { cartCount, clearCart, onCartChange } from './cart.js'

/**
 * Nav items: roles=null → luôn hiện (kể cả khách).
 * roles=[...] → hiện nếu user có ít nhất một role.
 */
const LINKS = [
  { href: '/home.html', label: 'Trang chủ', roles: null },
  { href: '/menu.html', label: 'Thực đơn', roles: null },
  { href: '/reservations.html', label: 'Đặt bàn', roles: ['CUSTOMER', 'ADMIN'] },
  { href: '/my-orders.html', label: 'Đơn của tôi', roles: ['CUSTOMER', 'ADMIN'] },
  { href: '/profile.html', label: 'Hồ sơ', roles: ['CUSTOMER', 'STAFF', 'KITCHEN', 'ADMIN'] },
  { href: '/staff/tables.html', label: 'Sơ đồ bàn', roles: ['STAFF', 'ADMIN'] },
  { href: '/staff/orders.html', label: 'Đơn hàng', roles: ['STAFF', 'ADMIN'] },
  { href: '/staff/reservations.html', label: 'Lịch đặt', roles: ['STAFF', 'ADMIN'] },
  { href: '/kitchen/', label: 'Bếp', roles: ['KITCHEN', 'STAFF', 'ADMIN'] },
  { href: '/admin/dashboard.html', label: 'Dashboard', roles: ['ADMIN'] },
  { href: '/admin/users.html', label: 'Users', roles: ['ADMIN'] },
  { href: '/admin/menu.html', label: 'Món', roles: ['ADMIN'] },
  { href: '/admin/categories.html', label: 'Danh mục', roles: ['ADMIN'] },
  { href: '/admin/tables.html', label: 'Bàn', roles: ['ADMIN'] },
  { href: '/admin/discounts.html', label: 'Giảm giá', roles: ['ADMIN'] },
  { href: '/admin/settings.html', label: 'Cài đặt', roles: ['ADMIN'] },
  { href: '/admin/audit.html', label: 'Audit', roles: ['ADMIN'] },
]

function normalizePath(pathname) {
  if (pathname.endsWith('/index.html')) {
    return pathname.replace(/index\.html$/, '')
  }
  return pathname
}

function isActive(href, pathname) {
  const path = normalizePath(pathname)
  const target = normalizePath(href)
  if (target === '/home.html') {
    return path === '/home.html' || path === '/'
  }
  if (target === '/kitchen/') {
    return path.startsWith('/kitchen')
  }
  return path === target || path.startsWith(target.replace(/\.html$/, ''))
}

function visibleLinks(me) {
  return LINKS.filter((link) => {
    if (link.roles == null) {
      return true
    }
    return me != null && hasAnyRole(me, link.roles)
  })
}

function render(me) {
  const path = window.location.pathname
  const links = visibleLinks(me)
    .map((link) => {
      const active = isActive(link.href, path) ? ' is-active' : ''
      return `<a class="app-nav-link${active}" href="${link.href}">${link.label}</a>`
    })
    .join('')

  const count = cartCount()
  const cartBit = me
    ? `<a class="app-nav-cart" id="app-nav-cart" href="/cart.html">Giỏ${count ? ` (${count})` : ''}</a>`
    : ''

  const authBit = me
    ? `<span class="app-nav-user">${escapeHtml(me.fullName)} · ${escapeHtml(me.roles.join(', '))}</span>
       ${cartBit}
       <button type="button" class="app-nav-logout" id="app-nav-logout">Đăng xuất</button>`
    : `<a class="app-nav-link" href="/">Đăng nhập</a>
       <a class="app-nav-link" href="/register.html">Đăng ký</a>`

  return `
    <div class="app-nav-inner">
      <a class="app-nav-brand" href="/home.html">RMS</a>
      <div class="app-nav-links">${links}</div>
      <div class="app-nav-auth">${authBit}</div>
    </div>
  `
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

/**
 * Gắn thanh nav theo vai vào #app-nav (tạo nếu thiếu).
 * @returns {Promise<object|null>} me
 */
export async function mountAppNav() {
  let el = document.querySelector('#app-nav')
  if (!el) {
    el = document.createElement('nav')
    el.id = 'app-nav'
    el.className = 'app-nav'
    document.body.prepend(el)
  } else {
    el.classList.add('app-nav')
  }

  const me = await fetchMe()
  el.innerHTML = render(me)

  const logoutBtn = el.querySelector('#app-nav-logout')
  if (logoutBtn) {
    logoutBtn.addEventListener('click', () => {
      clearCart()
      void logoutAndRedirect('/')
    })
  }

  const cartLink = el.querySelector('#app-nav-cart')
  if (cartLink) {
    onCartChange((items) => {
      const n = items.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
      cartLink.textContent = n ? `Giỏ (${n})` : 'Giỏ'
    })
  }

  // Ẩn thanh nav cứng trong HTML cũ (admin/staff/kitchen)
  document.querySelectorAll('.admin-nav').forEach((node) => {
    node.hidden = true
  })

  return me
}
