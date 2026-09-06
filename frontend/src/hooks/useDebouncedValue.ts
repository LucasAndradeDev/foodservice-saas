import { useEffect, useState } from 'react'

// Small shared version of the useState+useEffect+setTimeout debounce pattern already used twice
// in PublicMenuPage (CEP lookup, delivery fee quote) - avoids a third copy-paste.
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timeout)
  }, [value, delayMs])

  return debounced
}
