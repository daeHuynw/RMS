import { requireRoles } from '../../auth.js'

/** KITCHEN, STAFF or ADMIN. */
export async function requireKitchen() {
  return requireRoles(['KITCHEN', 'STAFF', 'ADMIN'])
}
