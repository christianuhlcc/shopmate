import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { apiClient } from '../../api/client'
import { useAuth } from './AuthContext'

export function AuthCallback() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const { setToken } = useAuth()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const code = searchParams.get('code')
    if (!code) {
      setError('No authorization code found in URL.')
      return
    }

    apiClient
      .POST('/auth/exchange', { body: { code } })
      .then(async ({ data, error: apiError }) => {
        if (apiError || !data) {
          setError('Authentication failed. Please try again.')
          return
        }
        await setToken(data.token)
        navigate('/lists', { replace: true })
      })
      .catch(() => {
        setError('Authentication failed. Please try again.')
      })
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-ground px-4">
        <div className="bg-panel rounded-2xl border border-line p-8 w-full max-w-sm text-center">
          <h2 className="text-title font-semibold text-ink mb-2">Sign-in didn't work</h2>
          <p className="text-body text-ink-soft mb-6">{error}</p>
          <a
            href="/login"
            className="pressable inline-flex items-center justify-center min-h-touch px-6 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep"
          >
            Back to sign-in
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-ground">
      <div className="flex flex-col items-center gap-4">
        <div
          className="h-10 w-10 animate-spin rounded-full border-4 border-marigold border-t-transparent"
          role="status"
          aria-label="Signing you in"
        />
        <p className="text-body text-ink-soft">Signing you in…</p>
      </div>
    </div>
  )
}
