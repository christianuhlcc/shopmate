import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { LoadingSpinner } from './LoadingSpinner'

interface ProtectedRouteProps {
  children: React.ReactNode
}

export function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { token, isLoading } = useAuth()

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
