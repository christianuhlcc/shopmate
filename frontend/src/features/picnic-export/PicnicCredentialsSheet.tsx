import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

type PicnicCredentialsStatus = components['schemas']['PicnicCredentialsStatus']

interface PicnicCredentialsSheetProps {
  onClose: () => void
  /**
   * Return to exporting. This sheet is only ever reached mid-export, so finishing a
   * link without offering the way back leaves the user on a dead end whose only
   * exits are the backdrop and Escape — neither of which reads as "continue".
   */
  onLinked?: () => void
}

const GENERIC_ERROR = 'Something went wrong. Please try again.'
const LOGIN_FAILED_ERROR = 'Picnic rejected these credentials — check your email and password.'
const UNAVAILABLE_ERROR = "Picnic isn't available right now — try again later."
const CODE_REJECTED_ERROR = "That code wasn't accepted — check it and try again."

/**
 * A Picnic outage and a wrong password both fail the link, but the user's next
 * move differs — retype the password vs. come back later — so they must not
 * share the generic copy.
 */
function submitErrorFor(code: string | undefined): string {
  if (code === 'PICNIC_LOGIN_FAILED') return LOGIN_FAILED_ERROR
  if (code === 'PICNIC_UNAVAILABLE') return UNAVAILABLE_ERROR
  return GENERIC_ERROR
}

/**
 * Bottom sheet for linking/unlinking the caller's Picnic account. Reuses the
 * sheet-backdrop/sheet-panel idiom from GroupSheet.
 */
export function PicnicCredentialsSheet({ onClose, onLinked }: PicnicCredentialsSheetProps) {
  const [status, setStatus] = useState<PicnicCredentialsStatus | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [isUnlinking, setIsUnlinking] = useState(false)

  const [code, setCode] = useState('')
  const [isVerifying, setIsVerifying] = useState(false)
  const [codeError, setCodeError] = useState<string | null>(null)
  const [isResending, setIsResending] = useState(false)
  const [resendNotice, setResendNotice] = useState<string | null>(null)

  // Picnic almost always demands an SMS code, so a "pending" link is the normal
  // path through this sheet rather than an edge case.
  const isAwaitingCode = status?.status === 'PENDING_SECOND_FACTOR'

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

    const { data, error: apiError } = await apiClient.PUT('/users/me/picnic-credentials', {
      body: { email: email.trim(), password },
    })

    setIsSubmitting(false)

    if (apiError || !data) {
      setSubmitError(submitErrorFor(apiError?.code))
      return
    }

    // The password is right either way; whether we are done depends on 2FA. Drop it
    // from state now so it is not sitting around during the code step.
    setPassword('')
    setStatus(
      data.status === 'PENDING_SECOND_FACTOR'
        ? { linked: false, email: email.trim(), status: 'PENDING_SECOND_FACTOR' }
        : { linked: true, email: email.trim(), status: 'LINKED' },
    )
  }

  async function handleVerify(event: FormEvent) {
    event.preventDefault()
    setIsVerifying(true)
    setCodeError(null)
    setResendNotice(null)

    const { error: apiError } = await apiClient.POST(
      '/users/me/picnic-credentials/2fa/verify',
      { body: { code: code.trim() } },
    )

    setIsVerifying(false)

    if (apiError) {
      // A rejected code leaves the pending link intact server-side, so staying on
      // this step lets the user retry without retyping their password.
      setCodeError(apiError.code === 'PICNIC_LOGIN_FAILED' ? CODE_REJECTED_ERROR : submitErrorFor(apiError.code))
      return
    }

    setCode('')
    setStatus({ linked: true, email: status?.email, status: 'LINKED' })
  }

  async function handleResend() {
    setIsResending(true)
    setCodeError(null)
    setResendNotice(null)

    const { error: apiError } = await apiClient.POST('/users/me/picnic-credentials/2fa/send')

    setIsResending(false)
    if (apiError) {
      setCodeError(submitErrorFor(apiError.code))
      return
    }
    setResendNotice('We sent a new code.')
  }

  async function handleUnlink() {
    setIsUnlinking(true)
    await apiClient.DELETE('/users/me/picnic-credentials')
    setIsUnlinking(false)
    setStatus({ linked: false })
    setEmail('')
    setPassword('')
    setCode('')
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
        <div className="flex items-start justify-between gap-3">
          <h2
            id="picnic-credentials-sheet-title"
            className="text-title font-semibold text-ink mb-1"
          >
            Picnic account
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="pressable -mt-1 -mr-1 min-h-touch min-w-touch rounded-full flex items-center justify-center text-ink-soft hover:bg-ground"
          >
            <svg className="w-5 h-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <path
                d="M6 6l8 8M14 6l-8 8"
                stroke="currentColor"
                strokeWidth="1.75"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
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
            {onLinked && (
              <button
                type="button"
                onClick={onLinked}
                className="pressable w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep"
              >
                Export this list
              </button>
            )}
            <button
              type="button"
              onClick={handleUnlink}
              disabled={isUnlinking}
              className="pressable mt-3 w-full min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground disabled:opacity-50"
            >
              {isUnlinking ? 'Unlinking…' : 'Unlink'}
            </button>
          </div>
        )}

        {isAwaitingCode && (
          <form onSubmit={handleVerify} className="mt-4">
            <p className="text-body text-ink-soft mb-4">
              Picnic sent a code by SMS. Enter it to finish linking
              {status?.email ? ' ' : ''}
              {status?.email && <span className="font-semibold text-ink">{status.email}</span>}.
            </p>

            <label
              htmlFor="picnic-code"
              className="block text-label font-semibold text-ink-soft mb-2"
            >
              SMS code
            </label>
            <input
              id="picnic-code"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
              className="w-full bg-panel border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-marigold-deep"
            />

            {codeError && (
              <p
                role="alert"
                className="mt-3 text-body text-danger bg-danger-tint border border-danger/25 rounded-xl px-3 py-2"
              >
                {codeError}
              </p>
            )}

            {resendNotice && (
              <p role="status" className="mt-3 text-body text-ink-soft">
                {resendNotice}
              </p>
            )}

            <button
              type="submit"
              disabled={isVerifying || !code.trim()}
              className="pressable mt-5 w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
            >
              {isVerifying ? 'Verifying…' : 'Verify code'}
            </button>

            <button
              type="button"
              onClick={handleResend}
              disabled={isResending}
              className="pressable mt-3 w-full min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground disabled:opacity-50"
            >
              {isResending ? 'Sending…' : 'Send a new code'}
            </button>
          </form>
        )}

        {status && !status.linked && !isAwaitingCode && (
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
