import { api, accessToken } from '../../api.js'
import { mountAppNav } from '../../nav.js'
import { clearCart, getCart, removeFromCart, setCartQty } from '../../cart.js'
import { requireRoles } from '../../auth.js'

await mountAppNav()
await requireRoles(['CUSTOMER', 'STAFF', 'KITCHEN', 'ADMIN'])

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const hintEl = document.querySelector('#hint')
const subEl = document.querySelector('#cart-sub')
const listEl = document.querySelector('#cart-items')
const checkoutEl = document.querySelector('#checkout')
const noteEl = document.querySelector('#customerNote')
const discountEl = document.querySelector('#discountCode')
const placeBtn = document.querySelector('#place-order')
const lastOrderEl = document.querySelector('#last-order')

/** @type {{ tableCode:string, tableSessionId:number } | null} */
let orderContext = null

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
}

function idempotencyKey() {
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID()
  return `ord-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function render() {
  const items = getCart()
  listEl.innerHTML = ''
  if (!items.length) {
    listEl.innerHTML = '<p class="muted">Giỏ trống. Vào <a href="/menu.html">thực đơn</a> thêm món.</p>'
    checkoutEl.hidden = true
    return
  }

  let total = 0
  for (const item of items) {
    total += Number(item.price) * Number(item.quantity)
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
        <button type="button" class="btn-secondary" data-rm="${item.id}">Xóa</button>
      </div>
    `
    listEl.append(row)
  }
  const sum = document.createElement('p')
  sum.innerHTML = `<strong>Tạm tính: ${money(total)}</strong>`
  listEl.append(sum)
  checkoutEl.hidden = false
}

listEl.addEventListener('click', (event) => {
  const btn = event.target.closest?.('button')
  if (!btn) return
  const id = Number(btn.getAttribute('data-inc') || btn.getAttribute('data-dec') || btn.getAttribute('data-rm'))
  if (!id) return
  const items = getCart()
  const item = items.find((i) => i.id === id)
  if (!item) return
  if (btn.hasAttribute('data-rm')) removeFromCart(id)
  else if (btn.hasAttribute('data-inc')) setCartQty(id, item.quantity + 1)
  else if (btn.hasAttribute('data-dec')) setCartQty(id, item.quantity - 1)
  render()
})

placeBtn.addEventListener('click', async () => {
  const items = getCart()
  if (!items.length) {
    flash('Giỏ trống', true)
    return
  }
  if (!orderContext) {
    flash('Chưa thể đặt — cần staff check-in sau khi đặt bàn', true)
    return
  }
  placeBtn.disabled = true
  try {
    const order = await api('/orders', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey() },
      body: JSON.stringify({
        items: items.map((i) => ({ menuItemId: i.id, quantity: i.quantity })),
        customerNote: noteEl.value.trim() || null,
        discountCode: discountEl.value.trim() || null,
      }),
    })
    clearCart()
    render()
    flash(`Đã đặt ${order.orderNumber}`, false)
    lastOrderEl.hidden = false
    lastOrderEl.textContent = `${order.orderNumber} · ${order.status} · ${money(order.totalAmount)}`
  } catch (err) {
    flash(err.message || 'Đặt món thất bại', true)
  } finally {
    placeBtn.disabled = false
  }
})

async function loadContext() {
  if (!accessToken()) return
  try {
    orderContext = await api('/orders/context')
    subEl.textContent = `Bàn ${orderContext.tableCode} · sẵn sàng đặt`
    hintEl.hidden = false
    hintEl.textContent = 'Chỉnh số lượng rồi bấm Đặt món.'
  } catch {
    orderContext = null
    hintEl.hidden = false
    hintEl.textContent =
      'Giỏ vẫn giữ món, nhưng chỉ đặt được sau khi staff check-in (xem Đặt bàn).'
  }
}

await loadContext()
render()
