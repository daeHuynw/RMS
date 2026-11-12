import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'
import { mountAppNav } from '../../nav.js'

await mountAppNav()

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#item-table')
const bodyEl = document.querySelector('#item-body')
const form = document.querySelector('#item-form')
const imageForm = document.querySelector('#image-form')
const imageHint = document.querySelector('#image-hint')
const categorySelect = document.querySelector('#categoryId')
const formTitle = document.querySelector('#form-title')
const resetBtn = document.querySelector('#reset-btn')

let categories = []
let items = []

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

function resetForm() {
  form.itemId.value = ''
  form.name.value = ''
  form.description.value = ''
  form.price.value = ''
  form.status.value = 'AVAILABLE'
  if (categories[0]) form.categoryId.value = categories[0].id
  formTitle.textContent = 'Thêm món'
  imageForm.hidden = true
  imageForm.file.value = ''
}

function fillCategories() {
  categorySelect.innerHTML = ''
  for (const cat of categories) {
    const opt = document.createElement('option')
    opt.value = cat.id
    opt.textContent = cat.active ? cat.name : `${cat.name} (off)`
    categorySelect.append(opt)
  }
}

function thumbHtml(url) {
  if (!url) return '<span class="thumb-empty">No img</span>'
  return `<img class="thumb" src="${escapeHtml(url)}" alt="" />`
}

function render() {
  bodyEl.innerHTML = ''
  for (const item of items) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td>${thumbHtml(item.imageUrl)}</td>
      <td>
        <strong>${escapeHtml(item.name)}</strong><br />
        <span class="muted">${escapeHtml(item.categoryName || '')}</span><br />
        <span class="muted">${escapeHtml(item.description || '')}</span>
      </td>
      <td>${money(item.price)}</td>
      <td><span class="badge ${item.status === 'AVAILABLE' ? 'badge-on' : 'badge-off'}">${escapeHtml(item.status)}</span></td>
      <td>
        <div class="admin-actions">
          <button type="button" class="btn-secondary" data-edit="${item.id}">Sửa</button>
          <button type="button" data-toggle="${item.id}" class="${item.status === 'AVAILABLE' ? 'btn-danger' : ''}">
            ${item.status === 'AVAILABLE' ? 'Ẩn' : 'Hiện'}
          </button>
        </div>
      </td>
    `
    bodyEl.append(tr)
  }
  tableEl.hidden = false
  loadingEl.hidden = true
}

async function load() {
  ;[categories, items] = await Promise.all([api('/admin/categories'), api('/admin/menu')])
  fillCategories()
  render()
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  const id = form.itemId.value
  const payload = {
    categoryId: Number(form.categoryId.value),
    name: form.name.value.trim(),
    description: form.description.value.trim() || null,
    price: Number(form.price.value),
    imageUrl: null,
    status: form.status.value,
  }
  try {
    if (id) {
      const current = items.find((i) => String(i.id) === id)
      payload.imageUrl = current?.imageUrl || null
      await api(`/admin/menu/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
      flash('Đã cập nhật món', false)
    } else {
      const created = await api('/admin/menu', { method: 'POST', body: JSON.stringify(payload) })
      flash('Đã thêm món', false)
      form.itemId.value = created.id
      formTitle.textContent = `Sửa #${created.id}`
      imageForm.hidden = false
      imageHint.textContent = `Upload ảnh cho món #${created.id}`
    }
    await load()
    if (!id) {
      const createdId = form.itemId.value
      const created = items.find((i) => String(i.id) === createdId)
      if (created) {
        form.itemId.value = created.id
        form.categoryId.value = created.categoryId
        form.name.value = created.name
        form.description.value = created.description || ''
        form.price.value = created.price
        form.status.value = created.status
        formTitle.textContent = `Sửa #${created.id}`
        imageForm.hidden = false
        imageHint.textContent = `Upload ảnh cho món #${created.id}`
      }
    } else {
      resetForm()
    }
  } catch (err) {
    flash(err.message || 'Lưu thất bại', true)
  }
})

imageForm.addEventListener('submit', async (event) => {
  event.preventDefault()
  const id = form.itemId.value
  const file = imageForm.file.files[0]
  if (!id || !file) {
    flash('Chọn món và file ảnh trước', true)
    return
  }
  const body = new FormData()
  body.append('file', file)
  try {
    await api(`/admin/menu/${id}/image`, { method: 'POST', body })
    flash('Đã upload ảnh', false)
    imageForm.file.value = ''
    await load()
  } catch (err) {
    flash(err.message || 'Upload thất bại', true)
  }
})

resetBtn.addEventListener('click', resetForm)

bodyEl.addEventListener('click', async (event) => {
  const editId = event.target.getAttribute?.('data-edit')
  const toggleId = event.target.getAttribute?.('data-toggle')
  if (editId) {
    const item = items.find((i) => String(i.id) === editId)
    if (!item) return
    form.itemId.value = item.id
    form.categoryId.value = item.categoryId
    form.name.value = item.name
    form.description.value = item.description || ''
    form.price.value = item.price
    form.status.value = item.status
    formTitle.textContent = `Sửa #${item.id}`
    imageForm.hidden = false
    imageHint.textContent = item.imageUrl ? `Ảnh hiện tại: ${item.imageUrl}` : 'Chưa có ảnh'
    return
  }
  if (toggleId) {
    const item = items.find((i) => String(i.id) === toggleId)
    if (!item) return
    const next = item.status === 'AVAILABLE' ? 'UNAVAILABLE' : 'AVAILABLE'
    try {
      await api(`/admin/menu/${item.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: next }),
      })
      flash(`Đã đổi sang ${next}`, false)
      await load()
    } catch (err) {
      flash(err.message || 'Đổi trạng thái thất bại', true)
    }
  }
})

await requireAdmin()
await load()
resetForm()
