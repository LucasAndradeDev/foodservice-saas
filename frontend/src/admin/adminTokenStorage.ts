// Entries written under this key before the memory-only migration held the raw admin JWT in the
// clear. Purge it once on load so a browser that already has this open doesn't keep carrying a
// live super-admin credential in localStorage indefinitely.
localStorage.removeItem('mora_admin_auth')

// Memory-only, not localStorage: an XSS payload can no longer walk off with this token via a
// one-off `localStorage.getItem(...)`, which matters more here than for a regular tenant session
// since this token can block/unblock any restaurant on the platform. There's no refresh
// mechanism for this account (see adminHttp.ts) - by design, a low-frequency, manually-triggered
// login - so the trade-off is that a page reload now requires logging back in.
let adminToken: string | null = null

export function getAdminToken(): string | null {
  return adminToken
}

export function setAdminToken(token: string): void {
  adminToken = token
}

export function clearAdminToken(): void {
  adminToken = null
}
