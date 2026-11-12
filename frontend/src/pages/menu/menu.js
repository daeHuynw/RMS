import { api, accessToken } from '../../api.js'
import { addToCart } from '../../cart.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const loadingEl = document.getElementById('menu-loading')
const errorEl = document.getElementById('menu-error')
const okEl = document.getElementById('menu-ok')
const hintEl = document.getElementById('order-hint')
const subEl = document.getElementById('menu-sub')
const filtersEl = document.getElementById('menu-filters')
const listEl = document.getElementById('menu-list')
const gotoCart = document.getElementById('goto-cart')

let catalog = { categories: [], items: [] }
let activeCategoryId = null
/** @type {{ reservationId:number, tableId:number, tableCode:string, tableSessionId:number } | null} */
let orderContext = null
let loggedIn = false

function showError(message) {
  errorEl.textContent = message
  errorEl.hidden = false
  okEl.hidden = true
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
    .replaceAll('"', '&quot;')
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID()
  return `ord-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function canOrder() {
  return orderContext != null
}

function renderFilters() {
  filtersEl.innerHTML = ''
  filtersEl.hidden = false

  const allBtn = document.createElement('button')
  allBtn.type = 'button'
  allBtn.className = 'chip' + (activeCategoryId == null ? ' chip-active' : '')
  allBtn.textContent = 'Tất cả'
  allBtn.addEventListener('click', () => {
    activeCategoryId = null
    render()
  })
  filtersEl.append(allBtn)

  for (const cat of catalog.categories) {
    const btn = document.createElement('button')
    btn.type = 'button'
    btn.className = 'chip' + (activeCategoryId === cat.id ? ' chip-active' : '')
    btn.textContent = cat.name
    btn.addEventListener('click', () => {
      activeCategoryId = cat.id
      render()
    })
    filtersEl.append(btn)
  }
}

function itemActions(item) {
  if (!loggedIn) return ''
  const add = `<button type="button" class="btn-add" data-cart="${item.id}"
      data-name="${escapeHtml(item.name)}" data-price="${item.price}">Thêm giỏ</button>`
  const orderNow = canOrder()
    ? `<button type="button" class="btn-add btn-order-now" data-order="${item.id}"
        data-name="${escapeHtml(item.name)}" data-price="${item.price}">Đặt ngay</button>`
    : ''
  return `<div class="menu-item-actions">${add}${orderNow}</div>`
}

function renderList() {
  listEl.innerHTML = ''
  listEl.hidden = false

  const items =
    activeCategoryId == null
      ? catalog.items
      : catalog.items.filter((item) => item.categoryId === activeCategoryId)

  if (items.length === 0) {
    const empty = document.createElement('p')
    empty.className = 'muted'
    empty.textContent = 'Không có món trong mục này.'
    listEl.append(empty)
    return
  }

  for (const item of items) {
    const row = document.createElement('article')
    row.className = 'menu-item'
    const img = item.imageUrl
      ? `<img class="menu-thumb" src="${escapeHtml(item.imageUrl)}" alt="" />`
      : `<span class="menu-thumb menu-thumb-empty"></span>`
    row.innerHTML = `
      ${img}
      <div class="menu-item-body">
        <h2>${escapeHtml(item.name)}</h2>
        <p class="muted">${escapeHtml(item.categoryName || '')}</p>
        <p class="muted">${escapeHtml(item.description || '')}</p>
        ${itemActions(item)}
      </div>
      <p class="price">${money(item.price)}</p>
    `
    listEl.append(row)
  }
}

function render() {
  renderFilters()
  renderList()
  gotoCart.hidden = !loggedIn
}

function payloadFromBtn(btn) {
  return {
    id: Number(btn.getAttribute('data-cart') || btn.getAttribute('data-order')),
    name: btn.getAttribute('data-name'),
    price: Number(btn.getAttribute('data-price')),
    quantity: 1,
  }
}

listEl.addEventListener('click', async (event) => {
  const cartBtn = event.target.closest?.('[data-cart]')
  const orderBtn = event.target.closest?.('[data-order]')
  if (cartBtn) {
    if (!loggedIn) {
      flash('Đăng nhập để thêm giỏ', true)
      return
    }
    addToCart(payloadFromBtn(cartBtn))
    flash(`Đã thêm «${cartBtn.getAttribute('data-name')}» vào giỏ`, false)
    return
  }
  if (orderBtn) {
    if (!canOrder()) {
      flash('Chưa check-in — chỉ thêm giỏ được, hoặc chờ staff check-in', true)
      return
    }
    const item = payloadFromBtn(orderBtn)
    orderBtn.disabled = true
    try {
      const order = await api('/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey() },
        body: JSON.stringify({
          items: [{ menuItemId: item.id, quantity: 1 }],
          customerNote: null,
          discountCode: null,
        }),
      })
      flash(`Đã đặt ngay ${order.orderNumber} · ${money(order.totalAmount)}`, false)
    } catch (err) {
      flash(err.message || 'Đặt ngay thất bại', true)
    } finally {
      orderBtn.disabled = false
    }
  }
})

async function loadContext() {
  loggedIn = Boolean(accessToken())
  if (!loggedIn) {
    hintEl.hidden = false
    hintEl.textContent = 'Xem món tự do. Đăng nhập → đặt bàn → check-in mới gọi món / thêm giỏ.'
    return
  }
  try {
    orderContext = await api('/orders/context')
    subEl.textContent = `Gọi món · bàn ${orderContext.tableCode}`
    hintEl.hidden = false
    hintEl.textContent =
      'Thêm giỏ (rồi mở Giỏ trên nav để đặt) hoặc Đặt ngay từng món.'
  } catch {
    orderContext = null
    hintEl.hidden = false
    hintEl.textContent =
      'Đã đăng nhập: có thể Thêm giỏ. Đặt món sau khi staff check-in (Đặt bàn → Gọi món).'
  }
}

async function load() {
  try {
    await loadContext()
    catalog = await api('/menu')
    loadingEl.hidden = true
    render()
  } catch (err) {
    loadingEl.hidden = true
    showError(err.message || 'Không tải được thực đơn')
  }
}

load()
