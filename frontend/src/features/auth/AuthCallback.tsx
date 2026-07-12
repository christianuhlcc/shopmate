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
      <div className="min-h-screen flex items-center justify-center bg-surface-muted px-4">
        <div className="bg-white rounded-2xl shadow-lg p-8 w-full max-w-sm text-center">
          <h2 className="text-lg font-semibold text-red-600 mb-4">Sign-in failed</h2>
          <p className="text-gray-600 mb-6">{error}</p>
          <a
            href="/login"
            className="inline-block px-4 py-2 bg-primary text-white rounded-lg font-medium hover:bg-primary-dark transition-colors"
          >
            Back to Login
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-surface-muted">
      <div className="flex flex-col items-center gap-4">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        <p className="text-gray-500">Signing you in…</p>
      </div>
    </div>
  )
}
