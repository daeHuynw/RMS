import { requireRoles } from '../../auth.js'

/** STAFF or ADMIN required. */
export async function requireStaff() {
  return requireRoles(['STAFF', 'ADMIN'])
}
