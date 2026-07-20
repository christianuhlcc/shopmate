import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { useAuth } from '../auth/AuthContext'
import { LoadingSpinner } from '../auth/LoadingSpinner'

// There is deliberately no endpoint to inspect an invite code's type up
// front. POST /invites/redeem with just { code } either succeeds, fails
// with a friendly reason, or — for a NEW_GROUP code — comes back 400
// GROUP_NAME_REQUIRED. That error does NOT consume the code, so we reveal
// a "name your household" field and resubmit the same code + a groupName.
const ERROR_MESSAGES: Record<string, string> = {
  INVITE_INVALID: "That invite code isn't valid. Double-check it and try again.",
  INVITE_EXPIRED: 'That invite code has expired — ask for a fresh one.',
  ALREADY_IN_GROUP: "You're already part of a household.",
}
const GENERIC_ERROR = 'Something went wrong. Please try again.'

export function OnboardingPage() {
  const { user, refreshUser } = useAuth()
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [groupName, setGroupName] = useState('')
  const [needsGroupName, setNeedsGroupName] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // A user who already has a group can't get stuck here (e.g. navigating
  // back after redeeming, or landing here directly with a stale link).
  useEffect(() => {
    if (user?.group) {
      navigate('/lists', { replace: true })
    }
  }, [user, navigate])

  if (user?.group) {
    return <LoadingSpinner />
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)

    const { data, error: apiError } = await apiClient.POST('/invites/redeem', {
      body: needsGroupName
        ? { code: code.trim(), groupName: groupName.trim() }
        : { code: code.trim() },
    })

    if (data) {
      await refreshUser()
      navigate('/lists', { replace: true })
      return
    }

    setIsSubmitting(false)

    if (apiError?.code === 'GROUP_NAME_REQUIRED') {
      setNeedsGroupName(true)
      return
    }

    setError((apiError?.code && ERROR_MESSAGES[apiError.code]) || GENERIC_ERROR)
  }

  function handleUseDifferentCode() {
    setNeedsGroupName(false)
    setCode('')
    setGroupName('')
    setError(null)
  }

  return (
    <div className="min-h-screen bg-marigold flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm">
        <header className="text-center mb-8">
          <h1 className="text-display font-bold text-ink tracking-tight">ShopMate</h1>
          <p className="mt-2 text-item font-medium text-honey-deep">
            {needsGroupName ? 'Name your household' : 'Join your household'}
          </p>
        </header>

        <div className="bg-panel rounded-2xl shadow-xl p-6">
          {!needsGroupName ? (
            <form onSubmit={handleSubmit}>
              <label
                htmlFor="invite-code"
                className="block text-label font-semibold text-ink-soft mb-2"
              >
                Invite code
              </label>
              <input
                id="invite-code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                placeholder="e.g. AB3DE9FG"
                autoComplete="off"
                autoFocus
                required
                className="w-full bg-panel border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-marigold-deep"
              />

              {error && (
                <p
                  role="alert"
                  className="mt-3 text-body text-danger bg-danger-tint border border-danger/25 rounded-xl px-3 py-2"
                >
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={isSubmitting || !code.trim()}
                className="pressable mt-5 w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
              >
                {isSubmitting ? 'Checking…' : 'Continue'}
              </button>
            </form>
          ) : (
            <form onSubmit={handleSubmit}>
              <p className="text-body text-ink-soft mb-4">
                This invite starts a brand-new household. Give it a name to get going.
              </p>

              <label
                htmlFor="group-name"
                className="block text-label font-semibold text-ink-soft mb-2"
              >
                Household name
              </label>
              <input
                id="group-name"
                type="text"
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
                placeholder="e.g. The Smiths"
                autoFocus
                required
                maxLength={100}
                className="w-full bg-panel border border-line rounded-xl px-4 py-3 text-body text-ink placeholder:text-ink-mute focus:outline-none focus:ring-2 focus:ring-marigold-deep"
              />

              {error && (
                <p
                  role="alert"
                  className="mt-3 text-body text-danger bg-danger-tint border border-danger/25 rounded-xl px-3 py-2"
                >
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={isSubmitting || !groupName.trim()}
                className="pressable mt-5 w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
              >
                {isSubmitting ? 'Creating…' : 'Create household'}
              </button>
              <button
                type="button"
                onClick={handleUseDifferentCode}
                className="pressable mt-3 w-full min-h-touch text-body font-semibold text-ink-soft hover:text-ink"
              >
                Use a different code
              </button>
            </form>
          )}
        </div>

        <p className="mt-4 text-center text-label text-honey-deep">
          Ask someone in your household for an invite code.
        </p>
      </div>
    </div>
  )
}
