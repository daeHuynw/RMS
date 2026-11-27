import { SERVER_UNREACHABLE, fetchMe, hasAnyRole, logoutAndRedirect, showPageError } from './auth.js'
import { cartCount, clearCart, onCartChange } from './cart.js'
import { escapeHtml } from './utils.js'

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

export const CART_ROLES = ['CUSTOMER', 'ADMIN']

function render(me, failed) {
  const path = window.location.pathname
  const links = visibleLinks(me)
    .map((link, i) => {
      const active = isActive(link.href, path) ? ' is-active' : ''
      return `<a class="app-nav-link${active}" style="--n:${i}" href="${link.href}">${link.label}</a>`
    })
    .join('')

  const count = cartCount()
  const cartBit = me && hasAnyRole(me, CART_ROLES)
    ? `<a class="app-nav-cart" id="app-nav-cart" href="/cart.html">Giỏ${count ? ` (${count})` : ''}</a>`
    : ''

  const logoutBtn = '<button type="button" class="app-nav-logout" id="app-nav-logout">Đăng xuất</button>'
  let authBit
  if (me) {
    authBit = `<span class="app-nav-user">${escapeHtml(me.fullName)} · ${escapeHtml(me.roles.join(', '))}</span>
       ${cartBit}
       ${logoutBtn}`
  } else if (failed) {
    authBit = `<span class="app-nav-user">Mất kết nối</span>${logoutBtn}`
  } else {
    authBit = `<a class="app-nav-link" href="/">Đăng nhập</a>
       <a class="app-nav-link" href="/register.html">Đăng ký</a>`
  }

  return `
    <div class="app-nav-inner">
      <a class="app-nav-brand" href="/home.html">RMS</a>
      <button type="button" class="app-nav-toggle" aria-expanded="false" aria-controls="app-nav-panel" aria-label="Mở menu điều hướng">
        <span class="app-nav-toggle-box" aria-hidden="true"><span></span><span></span></span>
      </button>
      <div class="app-nav-panel" id="app-nav-panel">
        <div class="app-nav-links">${links}</div>
        <div class="app-nav-auth">${authBit}</div>
      </div>
    </div>
  `
}

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

  let me = null
  let failed = false
  try {
    me = await fetchMe()
  } catch {
    failed = true
  }
  el.innerHTML = render(me, failed)
  if (failed) {
    showPageError(SERVER_UNREACHABLE)
  }

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

  document.querySelectorAll('.admin-nav').forEach((node) => {
    node.hidden = true
  })

  const toggle = el.querySelector('.app-nav-toggle')
  const panel = el.querySelector('#app-nav-panel')
  const setOpen = (open) => {
    el.classList.toggle('is-open', open)
    toggle.setAttribute('aria-expanded', String(open))
    toggle.setAttribute('aria-label', open ? 'Đóng menu điều hướng' : 'Mở menu điều hướng')
    document.documentElement.classList.toggle('nav-locked', open)
    if (open) {
      const first = panel.querySelector('a, button')
      if (first) first.focus()
    }
  }
  toggle.addEventListener('click', () => setOpen(!el.classList.contains('is-open')))
  panel.addEventListener('click', (e) => {
    if (e.target.closest('a')) setOpen(false)
  })
  addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && el.classList.contains('is-open')) {
      setOpen(false)
      toggle.focus()
    }
  })
  addEventListener('resize', () => {
    if (innerWidth >= 768 && el.classList.contains('is-open')) setOpen(false)
  })
  document.addEventListener('click', (e) => {
    if (el.classList.contains('is-open') && !el.contains(e.target)) setOpen(false)
  })
  const onScroll = () => el.classList.toggle('is-scrolled', scrollY > 8)
  addEventListener('scroll', onScroll, { passive: true })
  onScroll()

  return me
}