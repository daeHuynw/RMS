import { requireRoles } from '../../auth.js'

export async function requireCustomer() {
  return requireRoles(['CUSTOMER', 'ADMIN'])
}
