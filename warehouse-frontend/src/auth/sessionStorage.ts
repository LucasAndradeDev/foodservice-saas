export interface StoredSession {
  restaurantName: string
}

const SESSION_STORAGE_KEY = 'armazem_mora_restaurant'
const TOKEN_STORAGE_KEY = 'armazem_mora_token'

// Unlike Morá's access token (memory-only, silently refreshed via an httpOnly cookie on every
// reload), this one is persisted directly in localStorage. Armazém has no refresh flow - SSO
// handoff is the only entry point (see docs/ARMAZEM_MORA.md), there's no password login to fall
// back to - and this token only ever grants access to warehouse data (Insumos, Fornecedores,
// Compras, Receitas), never Morá's own session. Lower stakes, so a simpler v1 trade-off is fine:
// losing it on reload would otherwise force a trip back through the Morá sidebar every time.
export function getAccessToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function setAccessToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY)
  }
}

export function getStoredSession(): StoredSession | null {
  const raw = localStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as StoredSession
  } catch {
    return null
  }
}

export function setStoredSession(session: StoredSession): void {
  localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredSession(): void {
  localStorage.removeItem(SESSION_STORAGE_KEY)
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}
