import { api } from '../../api.js'
import { requireAdmin } from './requireAdmin.js'

const errorEl = document.querySelector('#error')
const okEl = document.querySelector('#ok')
const loadingEl = document.querySelector('#loading')
const tableEl = document.querySelector('#category-table')
const bodyEl = document.querySelector('#category-body')
const form = document.querySelector('#category-form')
const formTitle = document.querySelector('#form-title')
const resetBtn = document.querySelector('#reset-btn')

let categories = []

function flash(message, isError) {
  errorEl.hidden = !isError
  okEl.hidden = isError
  ;(isError ? errorEl : okEl).textContent = message
}

function resetForm() {
  form.categoryId.value = ''
  form.name.value = ''
  form.description.value = ''
  formTitle.textContent = 'Thêm danh mục'
}

function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

function render() {
  bodyEl.innerHTML = ''
  for (const cat of categories) {
    const tr = document.createElement('tr')
    tr.innerHTML = `
      <td>${escapeHtml(cat.name)}</td>
      <td class="muted">${escapeHtml(cat.description || '')}</td>
      <td><span class="badge ${cat.active ? 'badge-on' : 'badge-off'}">${cat.active ? 'Active' : 'Off'}</span></td>
      <td>
        <div class="admin-actions">
          <button type="button" data-edit="${cat.id}" class="btn-secondary">Sửa</button>
          <button type="button" data-toggle="${cat.id}" class="${cat.active ? 'btn-danger' : ''}">
            ${cat.active ? 'Tắt' : 'Bật'}
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
  categories = await api('/admin/categories')
  render()
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  const id = form.categoryId.value
  const payload = {
    name: form.name.value.trim(),
    description: form.description.value.trim() || null,
  }
  try {
    if (id) {
      await api(`/admin/categories/${id}`, { method: 'PUT', body: JSON.stringify(payload) })
      flash('Đã cập nhật danh mục', false)
    } else {
      await api('/admin/categories', { method: 'POST', body: JSON.stringify(payload) })
      flash('Đã thêm danh mục', false)
    }
    resetForm()
    await load()
  } catch (err) {
    flash(err.message || 'Lưu thất bại', true)
  }
})

resetBtn.addEventListener('click', resetForm)

bodyEl.addEventListener('click', async (event) => {
  const editId = event.target.getAttribute?.('data-edit')
  const toggleId = event.target.getAttribute?.('data-toggle')
  if (editId) {
    const cat = categories.find((c) => String(c.id) === editId)
    if (!cat) return
    form.categoryId.value = cat.id
    form.name.value = cat.name
    form.description.value = cat.description || ''
    formTitle.textContent = `Sửa #${cat.id}`
    return
  }
  if (toggleId) {
    const cat = categories.find((c) => String(c.id) === toggleId)
    if (!cat) return
    try {
      await api(`/admin/categories/${cat.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ active: !cat.active }),
      })
      flash(cat.active ? 'Đã tắt danh mục' : 'Đã bật danh mục', false)
      await load()
    } catch (err) {
      flash(err.message || 'Đổi trạng thái thất bại', true)
    }
  }
})

await requireAdmin()
await load()
