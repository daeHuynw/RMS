// Hàm dùng chung cho mọi trang (REVIEW C5): trước đây mỗi trang chép một bản riêng,
// một số bản quên escape dấu nháy.

/** Escape để chèn vào HTML — cả nội dung lẫn giá trị thuộc tính trong "..." / '...'. */
export function escapeHtml(text) {
  return String(text ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const VND = new Intl.NumberFormat('vi-VN', {
  style: 'currency',
  currency: 'VND',
  maximumFractionDigits: 0,
})

export function money(value) {
  return VND.format(Number(value || 0))
}

/** Ngày + giờ theo máy người xem; rỗng nếu không có giá trị. */
export function formatDateTime(iso) {
  return iso ? new Date(iso).toLocaleString('vi-VN') : ''
}

/** Chỉ giờ (màn bếp). */
export function formatTime(iso) {
  return iso ? new Date(iso).toLocaleTimeString('vi-VN') : ''
}

// crypto.randomUUID chỉ có trong "secure context" (https hoặc localhost);
// mở app qua http://<IP LAN> thì không có → cần dự phòng.
function newKey() {
  if (globalThis.crypto?.randomUUID) return crypto.randomUUID()
  if (globalThis.crypto?.getRandomValues) {
    const bytes = crypto.getRandomValues(new Uint8Array(16))
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
  }
  return `k-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

const pendingKeys = new Map()

/**
 * Gửi một thao tác POST có Idempotency-Key. `scope` đặt tên thao tác ("cart", "pay:12"…),
 * `body` là chuỗi JSON sẽ gửi, `send(key)` thực hiện api(...) với key đó.
 *
 * - Bấm lại với đúng `body` cũ sau lỗi mạng → dùng lại key cũ: nếu lần trước server đã lưu
 *   mà mất phản hồi, server trả lại đúng bản ghi đó thay vì tạo trùng.
 * - `body` khác → key mới.
 * - Chỉ giữ key khi không biết server đã lưu hay chưa (không có status, hoặc 5xx).
 *   Thành công hay 4xx → bỏ key, lần bấm sau là thao tác mới.
 * Key chỉ nằm trong bộ nhớ trang; tải lại trang là mất.
 */
export async function sendIdempotent(scope, body, send) {
  const saved = pendingKeys.get(scope)
  const key = saved && saved.body === body ? saved.key : newKey()
  pendingKeys.set(scope, { body, key })
  try {
    const result = await send(key)
    pendingKeys.delete(scope)
    return result
  } catch (err) {
    const unknownOutcome = err.status == null || err.status >= 500
    if (!unknownOutcome) {
      pendingKeys.delete(scope)
    }
    throw err
  }
}

/** Câu báo lỗi cho nút gửi dùng sendIdempotent: lỗi mạng thì nói rõ bấm lại là an toàn. */
export function sendErrorText(err, fallback) {
  if (err.status == null) {
    return 'Mất kết nối máy chủ — bấm lại để gửi lại (không tạo trùng).'
  }
  return err.message || fallback
}
