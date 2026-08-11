import { useEffect, useState } from 'react'
import { NETWORK_STATUS_EVENT } from '../api/http'

export function useOnlineStatus(): boolean {
  const [isOnline, setIsOnline] = useState(navigator.onLine)

  useEffect(() => {
    function handleBrowserOffline() {
      setIsOnline(false)
    }
    function handleBrowserOnline() {
      setIsOnline(true)
    }
    // The browser's online/offline events only reflect the network interface -- a wifi AP with
    // no real internet still reports "online". The API interceptor's per-request signal (see
    // http.ts) catches that case too, so we combine both.
    function handleNetworkStatus(event: Event) {
      setIsOnline((event as CustomEvent<{ online: boolean }>).detail.online)
    }

    window.addEventListener('offline', handleBrowserOffline)
    window.addEventListener('online', handleBrowserOnline)
    window.addEventListener(NETWORK_STATUS_EVENT, handleNetworkStatus)
    return () => {
      window.removeEventListener('offline', handleBrowserOffline)
      window.removeEventListener('online', handleBrowserOnline)
      window.removeEventListener(NETWORK_STATUS_EVENT, handleNetworkStatus)
    }
  }, [])

  return isOnline
}
