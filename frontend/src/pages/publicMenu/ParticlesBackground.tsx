import type { Engine, ISourceOptions } from '@tsparticles/engine'
import Particles, { ParticlesProvider } from '@tsparticles/react'
import { loadSlim } from '@tsparticles/slim'
import { useMemo } from 'react'

// Must be a stable reference (module scope, not re-created per render) — ParticlesProvider
// throws if it receives a different `init` function across renders of the same subtree.
async function initEngine(engine: Engine) {
  await loadSlim(engine)
}

const reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

interface ParticlesBackgroundProps {
  theme: 'light' | 'dark'
}

/**
 * Warm, slow-drifting "embers" behind the menu content — like dust motes catching light.
 * Purely decorative (non-interactive, `-z-10`, `pointer-events-none`): it never intercepts
 * taps/scrolls, and respects `prefers-reduced-motion` by rendering a static frame instead.
 */
export function ParticlesBackground({ theme }: ParticlesBackgroundProps) {
  const options: ISourceOptions = useMemo(
    () => ({
      fullScreen: { enable: false },
      fpsLimit: 30,
      background: { color: { value: 'transparent' } },
      particles: {
        number: { value: 34 },
        color: { value: theme === 'dark' ? ['#dd7455', '#d8af61', '#eea087'] : ['#c6532f', '#b98726', '#dd7455'] },
        shape: { type: 'circle' },
        opacity: {
          value: { min: 0.08, max: theme === 'dark' ? 0.4 : 0.25 },
          animation: reducedMotion ? undefined : { enable: true, speed: 0.5, sync: false },
        },
        size: { value: { min: 1.5, max: 5 } },
        move: {
          enable: !reducedMotion,
          speed: { min: 0.2, max: 0.6 },
          direction: 'none',
          random: true,
          straight: false,
          outModes: { default: 'out' },
        },
        links: { enable: false },
      },
      interactivity: {
        events: { onHover: { enable: false }, onClick: { enable: false }, resize: { enable: true } },
      },
      detectRetina: true,
    }),
    [theme],
  )

  return (
    <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <ParticlesProvider init={initEngine}>
        <Particles id="menu-ambient-particles" options={options} className="h-full w-full" />
      </ParticlesProvider>
    </div>
  )
}
