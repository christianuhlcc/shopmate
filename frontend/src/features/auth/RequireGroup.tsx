import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { LoadingSpinner } from './LoadingSpinner'

interface RequireGroupProps {
  children: React.ReactNode
}

// Sibling of ProtectedRoute: gates on group membership rather than auth.
// A user without a group can't see any lists until they redeem an invite
// code on the welcome screen.
export function RequireGroup({ children }: RequireGroupProps) {
  const { user, isLoading } = useAuth()

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!user?.group) {
    return <Navigate to="/welcome" replace />
  }

  return <>{children}</>
}
