import { api } from '../../api.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const params = new URLSearchParams(window.location.search)
let token = (params.get('token') || '').trim()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const infoEl = document.querySelector('#table-info')
const listEl = document.querySelector('#menu-list')
const cartEl = document.querySelector('#cart')
const cartItemsEl = document.querySelector('#cart-items')
const noteEl = document.querySelector('#customerNote')
const discountEl = document.querySelector('#discountCode')
const placeBtn = document.querySelector('#place-order')
const lastOrderEl = document.querySelector('#last-order')
const tokenForm = document.querySelector('#token-form')
const tokenInput = document.querySelector('#token-input')

/** @type {Map<number, { id:number, name:string, price:number, quantity:number }>} */
const cart = new Map()

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

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return crypto.randomUUID()
  }
  return `qr-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function qrPath(suffix) {
  return `/qr${suffix}?token=${encodeURIComponent(token)}`
}

function renderCart() {
  cartItemsEl.innerHTML = ''
  if (!cart.size) {
    cartItemsEl.innerHTML = '<p class="muted">Chưa chọn món.</p>'
    return
  }
  for (const item of cart.values()) {
    const row = document.createElement('div')
    row.className = 'cart-row'
    row.innerHTML = `
      <div>
        <strong>${escapeHtml(item.name)}</strong>
        <span class="muted"> · ${money(item.price)}</span>
      </div>
      <div class="cart-qty">
        <button type="button" data-dec="${item.id}">−</button>
        <span>${item.quantity}</span>
        <button type="button" data-inc="${item.id}">+</button>
      </div>
    `
    cartItemsEl.append(row)
  }
}

function addToCart(item) {
  const current = cart.get(item.id)
  if (current) {
    current.quantity = Math.min(99, current.quantity + 1)
  } else {
    cart.set(item.id, { id: item.id, name: item.name, price: Number(item.price), quantity: 1 })
  }
  cartEl.hidden = false
  renderCart()
}

listEl.addEventListener('click', (event) => {
  const btn = event.target.closest?.('[data-add]')
  if (!btn) return
  addToCart({
    id: Number(btn.getAttribute('data-add')),
    name: btn.getAttribute('data-name'),
    price: btn.getAttribute('data-price'),
  })
})

cartItemsEl.addEventListener('click', (event) => {
  const btn = event.target.closest?.('button')
  if (!btn) return
  const inc = btn.getAttribute('data-inc')
  const dec = btn.getAttribute('data-dec')
  const key = Number(inc || dec)
  if (!key || !cart.has(key)) return
  const item = cart.get(key)
  if (inc) item.quantity = Math.min(99, item.quantity + 1)
  if (dec) {
    item.quantity -= 1
    if (item.quantity < 1) cart.delete(key)
  }
  renderCart()
})

placeBtn.addEventListener('click', async () => {
  if (!token) {
    flash('Thiếu token bàn', true)
    return
  }
  if (!cart.size) {
    flash('Chọn ít nhất một món', true)
    return
  }
  placeBtn.disabled = true
  try {
    const order = await api(qrPath('/orders'), {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: JSON.stringify({
        items: [...cart.values()].map((i) => ({
          menuItemId: i.id,
          quantity: i.quantity,
        })),
        customerNote: noteEl.value.trim() || null,
        discountCode: discountEl.value.trim() || null,
      }),
    })
    cart.clear()
    renderCart()
    const discountNote = Number(order.discountAmount) > 0
      ? ` · giảm ${money(order.discountAmount)}`
      : ''
    flash(`Đã gửi đơn ${order.orderNumber}`, false)
    lastOrderEl.hidden = false
    lastOrderEl.textContent = `${order.orderNumber} · ${order.status} · ${money(order.totalAmount)}${discountNote}`
  } catch (err) {
    flash(err.message || 'Gửi đơn thất bại', true)
  } finally {
    placeBtn.disabled = false
  }
})

tokenForm.addEventListener('submit', (event) => {
  event.preventDefault()
  const next = tokenInput.value.trim()
  if (!next) return
  window.location.href = `/qr.html?token=${encodeURIComponent(next)}`
})

async function load() {
  if (!token) {
    infoEl.textContent = 'Chưa có token bàn'
    tokenForm.hidden = false
    flash('Mở link QR từ Admin/Staff (Bàn → QR), hoặc dán token bên dưới.', true)
    return
  }
  tokenForm.hidden = true
  try {
    const data = await api(qrPath('/menu'))
    infoEl.textContent = `Bàn ${data.tableCode} · phiên #${data.sessionId}`
    listEl.innerHTML = ''
    if (!data.items?.length) {
      listEl.innerHTML = '<p class="muted">Chưa có món AVAILABLE — admin cần thêm món đang bán.</p>'
    } else {
      for (const item of data.items) {
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
            <button type="button" class="btn-add"
              data-add="${item.id}"
              data-name="${escapeHtml(item.name)}"
              data-price="${item.price}">Thêm</button>
          </div>
          <p class="price">${money(item.price)}</p>
        `
        listEl.append(row)
      }
    }
    listEl.hidden = false
    cartEl.hidden = false
    renderCart()
  } catch (err) {
    infoEl.textContent = 'Không mở được bàn'
    tokenForm.hidden = false
    tokenInput.value = token
    flash(err.message || 'QR không hợp lệ — thử xoay QR mới ở Admin bàn', true)
  }
}

load()
