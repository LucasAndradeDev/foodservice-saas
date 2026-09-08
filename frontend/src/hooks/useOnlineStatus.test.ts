import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { NETWORK_STATUS_EVENT } from '../api/http'
import { useOnlineStatus } from './useOnlineStatus'

afterEach(() => {
  Object.defineProperty(navigator, 'onLine', { value: true, configurable: true })
})

describe('useOnlineStatus', () => {
  it('reflects navigator.onLine on mount', () => {
    Object.defineProperty(navigator, 'onLine', { value: false, configurable: true })
    const { result } = renderHook(() => useOnlineStatus())
    expect(result.current).toBe(false)
  })

  it('goes offline on the browser offline event', () => {
    const { result } = renderHook(() => useOnlineStatus())
    expect(result.current).toBe(true)
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(result.current).toBe(false)
  })

  it('goes back online on the browser online event', () => {
    const { result } = renderHook(() => useOnlineStatus())
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(result.current).toBe(false)
    act(() => {
      window.dispatchEvent(new Event('online'))
    })
    expect(result.current).toBe(true)
  })

  it('follows the app-level network status event from the API interceptor', () => {
    const { result } = renderHook(() => useOnlineStatus())
    act(() => {
      window.dispatchEvent(new CustomEvent(NETWORK_STATUS_EVENT, { detail: { online: false } }))
    })
    expect(result.current).toBe(false)
  })
})
