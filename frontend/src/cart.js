const KEY = 'rmsCart'
const listeners = new Set()

function read() {
  try {
    const raw = localStorage.getItem(KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch {
    return []
  }
}

function write(items) {
  localStorage.setItem(KEY, JSON.stringify(items))
  listeners.forEach((fn) => {
    try {
      fn(items)
    } catch {
      // ignore subscriber errors
    }
  })
}

export function getCart() {
  return read()
}

export function cartCount() {
  return read().reduce((sum, item) => sum + Number(item.quantity || 0), 0)
}

export function onCartChange(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

export function addToCart({ id, name, price, quantity = 1 }) {
  const items = read()
  const found = items.find((i) => i.id === id)
  if (found) {
    found.quantity = Math.min(99, found.quantity + quantity)
  } else {
    items.push({ id, name, price: Number(price), quantity: Math.min(99, quantity) })
  }
  write(items)
  return items
}

export function setCartQty(id, quantity) {
  let items = read()
  if (quantity < 1) {
    items = items.filter((i) => i.id !== id)
  } else {
    const found = items.find((i) => i.id === id)
    if (found) found.quantity = Math.min(99, quantity)
  }
  write(items)
  return items
}

export function removeFromCart(id) {
  write(read().filter((i) => i.id !== id))
}

export function clearCart() {
  write([])
}
