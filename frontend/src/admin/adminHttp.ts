import axios from 'axios'
import { clearAdminToken, getAdminToken } from './adminTokenStorage'

export const ADMIN_LOGOUT_EVENT = 'admin:logout'

export const adminHttp = axios.create({
  baseURL: '/api/v1/admin',
})

adminHttp.interceptors.request.use((config) => {
  const token = getAdminToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// No refresh-token flow for the admin session — it's a low-frequency, manually-triggered
// login (12h token), so on 401 we just force a re-login instead of building a second
// refresh mechanism for a single user.
adminHttp.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAdminToken()
      window.dispatchEvent(new Event(ADMIN_LOGOUT_EVENT))
    }
    return Promise.reject(error)
  },
)
