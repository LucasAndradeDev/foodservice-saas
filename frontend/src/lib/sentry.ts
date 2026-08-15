import * as Sentry from '@sentry/react'

const dsn = import.meta.env.VITE_SENTRY_DSN

// Blank dsn (local dev, and any deploy that hasn't set it) leaves the SDK uninitialized -
// Sentry.captureException etc. become no-ops, same disabled-by-default posture as the backend.
if (dsn) {
  Sentry.init({
    dsn,
    environment: import.meta.env.VITE_SENTRY_ENVIRONMENT ?? 'development',
    // No performance tracing/session replay - error reporting only, to stay on Sentry's free tier.
  })
}
