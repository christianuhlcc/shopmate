import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

type PicnicCredentialsStatus = components['schemas']['PicnicCredentialsStatus']

interface PicnicCredentialsSheetProps {
  onClose: () => void
}

const GENERIC_ERROR = 'Something went wrong. Please try again.'
const LOGIN_FAILED_ERROR = 'Picnic rejected these credentials — check your email and password.'

/**
 * Bottom sheet for linking/unlinking the caller's Picnic account. Reuses the
 * sheet-backdrop/sheet-panel idiom from GroupSheet.
 */
export function PicnicCredentialsSheet({ onClose }: PicnicCredentialsSheetProps) {
  const [status, setStatus] = useState<PicnicCredentialsStatus | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [isUnlinking, setIsUnlinking] = useState(false)

  useEffect(() => {
    apiClient
      .GET('/users/me/picnic-credentials')
      .then(({ data, error: apiError }) => {
        if (apiError || !data) {
          setLoadError('Could not load your Picnic account status.')
          return
        }
        setStatus(data)
      })
      .catch(() => setLoadError('Could not load your Picnic account status.'))
      .finally(() => setIsLoading(false))
  }, [])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setIsSubmitting(true)
    setSubmitError(null)

    const { error: apiError } = await apiClient.PUT('/users/me/picnic-credentials', {
      body: { email: email.trim(), password },
    })

    setIsSubmitting(false)

    if (apiError) {
      setSubmitError(apiError.code === 'PICNIC_LOGIN_FAILED' ? LOGIN_FAILED_ERROR : GENERIC_ERROR)
      return
    }

    setStatus({ linked: true, email: email.trim() })
    setPassword('')
  }

  async function handleUnlink() {
    setIsUnlinking(true)
    await apiClient.DELETE('/users/me/picnic-credentials')
    setIsUnlinking(false)
    setStatus({ linked: false })
    setEmail('')
    setPassword('')
  }

  return (
    <div
      className="sheet-backdrop fixed inset-0 bg-ink/40 flex items-end sm:items-center justify-center z-overlay px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:pb-0"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="picnic-credentials-sheet-title"
        className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet max-h-[80vh] overflow-y-auto"
      >
        <h2
          id="picnic-credentials-sheet-title"
          className="text-title font-semibold text-ink mb-1"
        >
          Picnic account
        </h2>
        <span className="inline-block mb-3 text-label font-semibold text-honey-deep bg-marigold-tint rounded-full px-2 py-0.5">
          Beta — uses an unofficial Picnic API
        </span>

        {isLoading && (
          <div role="status" aria-label="Loading Picnic account status" className="space-y-2 mt-4">
            <div className="h-4 w-2/5 rounded bg-line animate-pulse" />
            <div className="h-3 w-3/5 rounded bg-line/70 animate-pulse" />
          </div>
        )}

        {loadError && (
          <div className="bg-danger-tint border border-danger/25 rounded-xl p-4 mt-4 text-danger text-body">
            {loadError}
          </div>
        )}

        {status?.linked && (
          <div className="mt-4">
            <p className="text-body text-ink-soft mb-1">
              Your Picnic account is linked for grocery export.
            </p>
            <p className="text-body font-semibold text-ink mb-4">{status.email}</p>
            <button
              type="button"
              onClick={handleUnlink}
              disabled={isUnlinking}
              className="pressable w-full min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground disabled:opacity-50"
            >
              {isUnlinking ? 'Unlinking…' : 'Unlink'}
            </button>
          </div>
        )}

        {status && !status.linked && (
          <form onSubmit={handleSubmit} className="mt-4">
            <label
              htmlFor="picnic-email"
              className="block text-label font-semibold text-ink-soft mb-2"
            >
              Email
            </label>
            <input
              id="picnic-email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
              required
              className="w-full bg-panel border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-marigold-deep"
            />

            <label
              htmlFor="picnic-password"
              className="block text-label font-semibold text-ink-soft mb-2 mt-4"
            >
              Password
            </label>
            <input
              id="picnic-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              className="w-full bg-panel border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-marigold-deep"
            />

            {submitError && (
              <p
                role="alert"
                className="mt-3 text-body text-danger bg-danger-tint border border-danger/25 rounded-xl px-3 py-2"
              >
                {submitError}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting || !email.trim() || !password}
              className="pressable mt-5 w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
            >
              {isSubmitting ? 'Linking…' : 'Link account'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
