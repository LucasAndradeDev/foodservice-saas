import axios, { type AxiosError } from 'axios'
import { clearStoredSession, getAccessToken } from '../auth/sessionStorage'

export const AUTH_LOGOUT_EVENT = 'auth:logout'

export const http = axios.create({
  baseURL: '/api/v1',
})

http.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// No refresh-and-retry flow here (unlike Morá's http.ts) - the session token has no refresh
// mechanism (see sessionStorage.ts), so on a 401 the only real recovery is going back through
// Morá's SSO handoff. Just clear the stale session and let ProtectedRoute redirect away.
http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      clearStoredSession()
      window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
    }
    return Promise.reject(error)
  },
)
