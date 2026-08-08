import { lazy, Suspense } from 'react'

// Lazy: tsparticles' engine is only needed on the public menu, never on the admin panel — code
// splitting keeps its weight out of every other route's bundle.
const ParticlesBackground = lazy(() =>
  import('./ParticlesBackground').then((module) => ({ default: module.ParticlesBackground })),
)

interface AmbientBackgroundProps {
  theme: 'light' | 'dark'
}

export function AmbientBackground({ theme }: AmbientBackgroundProps) {
  return (
    <Suspense fallback={null}>
      <ParticlesBackground theme={theme} />
    </Suspense>
  )
}
