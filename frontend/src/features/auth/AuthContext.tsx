import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react'
import { useNavigate } from 'react-router-dom'
import { apiClient, setApiToken } from '../../api/client'

interface UserProfile {
  id: string
  email: string
  displayName: string
  avatarUrl?: string | null
}

interface AuthContextValue {
  token: string | null
  user: UserProfile | null
  isLoading: boolean
  login: () => void
  logout: () => void
  setToken: (token: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() =>
    localStorage.getItem('auth_token'),
  )
  const [user, setUser] = useState<UserProfile | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(() => {
    // If there's a stored token we need to fetch the user profile
    return localStorage.getItem('auth_token') !== null
  })
  const navigate = useNavigate()

  // On mount, if we already have a stored token, fetch the user profile
  useEffect(() => {
    const stored = localStorage.getItem('auth_token')
    if (stored && !user) {
      setApiToken(stored)
      apiClient
        .GET('/users/me')
        .then(({ data }) => {
          if (data) {
            setUser(data)
          } else {
            // Token invalid — clear it
            localStorage.removeItem('auth_token')
            setTokenState(null)
            setApiToken(null)
          }
        })
        .catch(() => {
          localStorage.removeItem('auth_token')
          setTokenState(null)
          setApiToken(null)
        })
        .finally(() => setIsLoading(false))
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(() => {
    window.location.href = '/oauth2/authorization/google'
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token')
    setTokenState(null)
    setApiToken(null)
    setUser(null)
    navigate('/login')
  }, [navigate])

  const setToken = useCallback(async (newToken: string) => {
    localStorage.setItem('auth_token', newToken)
    setTokenState(newToken)
    setApiToken(newToken)
    const { data } = await apiClient.GET('/users/me')
    if (data) {
      setUser(data)
    }
  }, [])

  return (
    <AuthContext.Provider value={{ token, user, isLoading, login, logout, setToken }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return ctx
}
