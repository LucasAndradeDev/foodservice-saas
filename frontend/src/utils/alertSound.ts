export type AlertSection = 'KITCHEN' | 'TABLES' | 'CHECKOUT'

/** Distinct pitch per section so staff can tell alerts apart by ear without looking at the screen. */
const FREQUENCY_HZ: Record<AlertSection, number> = {
  KITCHEN: 660,
  TABLES: 880,
  CHECKOUT: 1046,
}

export function playAlertTone(section: AlertSection) {
  try {
    const ctx = new AudioContext()
    const oscillator = ctx.createOscillator()
    const gain = ctx.createGain()
    oscillator.type = 'sine'
    oscillator.frequency.value = FREQUENCY_HZ[section]
    gain.gain.setValueAtTime(0.15, ctx.currentTime)
    oscillator.connect(gain)
    gain.connect(ctx.destination)
    oscillator.start()
    oscillator.stop(ctx.currentTime + 0.2)
  } catch {
    // Audio isn't critical to the feature; ignore if the browser blocks it.
  }
}
