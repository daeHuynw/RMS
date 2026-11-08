import { api } from '../../api.js'

const loadingEl = document.getElementById('menu-loading')
const errorEl = document.getElementById('menu-error')
const filtersEl = document.getElementById('menu-filters')
const listEl = document.getElementById('menu-list')

let catalog = { categories: [], items: [] }
let activeCategoryId = null // null = tất cả

function showError(message) {
  errorEl.textContent = message
  errorEl.hidden = false
}

function money(value) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(Number(value))
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
      </div>
      <p class="price">${money(item.price)}</p>
    `
    listEl.append(row)
  }
}

function escapeHtml(text) {
  return String(text)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function render() {
  renderFilters()
  renderList()
}

async function load() {
  try {
    catalog = await api('/menu')
    loadingEl.hidden = true
    render()
  } catch (err) {
    loadingEl.hidden = true
    showError(err.message || 'Không tải được thực đơn')
  }
}

load()