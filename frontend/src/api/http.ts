import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearStoredAuth, getStoredAuth, updateStoredTokens } from '../auth/tokenStorage'

export const AUTH_LOGOUT_EVENT = 'auth:logout'
export const NETWORK_STATUS_EVENT = 'network:status'

function reportNetworkStatus(online: boolean) {
  window.dispatchEvent(new CustomEvent<{ online: boolean }>(NETWORK_STATUS_EVENT, { detail: { online } }))
}

export const http = axios.create({
  baseURL: '/api/v1',
})

http.interceptors.request.use((config) => {
  const auth = getStoredAuth()
  if (auth?.accessToken) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`
  }
  return config
})

interface RetryableConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let isRefreshing = false
let pendingRequests: Array<(token: string) => void> = []

function onTokenRefreshed(newToken: string) {
  pendingRequests.forEach((callback) => callback(newToken))
  pendingRequests = []
}

http.interceptors.response.use(
  (response) => {
    reportNetworkStatus(true)
    return response
  },
  async (error: AxiosError) => {
    // No `response` at all means the request never reached the server -- dropped wifi, DNS
    // failure, etc -- as opposed to a normal 4xx/5xx, which means the connection is fine.
    reportNetworkStatus(!!error.response)

    const originalRequest = error.config as RetryableConfig | undefined

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.url === '/auth/refresh-token'
    ) {
      return Promise.reject(error)
    }

    const auth = getStoredAuth()
    if (!auth?.refreshToken) {
      return Promise.reject(error)
    }

    originalRequest._retry = true

    if (isRefreshing) {
      return new Promise((resolve) => {
        pendingRequests.push((newToken) => {
          originalRequest.headers.Authorization = `Bearer ${newToken}`
          resolve(http(originalRequest))
        })
      })
    }

    isRefreshing = true
    try {
      const { data } = await axios.post('/api/v1/auth/refresh-token', {
        refreshToken: auth.refreshToken,
      })
      updateStoredTokens(data.accessToken, data.refreshToken)
      onTokenRefreshed(data.accessToken)
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`
      return http(originalRequest)
    } catch (refreshError) {
      clearStoredAuth()
      window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)
