import { requireRoles } from '../../auth.js'

/** Redirect unless current user has ADMIN. */
export async function requireAdmin() {
  return requireRoles(['ADMIN'])
}
