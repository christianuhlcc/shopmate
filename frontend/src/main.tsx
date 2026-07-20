import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import './index.css'
import { initObservability } from './observability'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { RequireGroup } from './features/auth/RequireGroup'
import { LoginPage } from './features/auth/LoginPage'
import { AuthCallback } from './features/auth/AuthCallback'
import { OnboardingPage } from './features/onboarding/OnboardingPage'
import { ListsPage } from './features/shopping-list/components/ListsPage'
import { ShoppingListPage } from './features/shopping-list/components/ShoppingListPage'

initObservability()

const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/lists" replace />,
  },
  {
    path: '/login',
    element: <AuthProvider><LoginPage /></AuthProvider>,
  },
  {
    path: '/auth/callback',
    element: <AuthProvider><AuthCallback /></AuthProvider>,
  },
  {
    path: '/welcome',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <OnboardingPage />
        </ProtectedRoute>
      </AuthProvider>
    ),
  },
  {
    path: '/lists',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <RequireGroup>
            <ListsPage />
          </RequireGroup>
        </ProtectedRoute>
      </AuthProvider>
    ),
  },
  {
    path: '/lists/:listId',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <RequireGroup>
            <ShoppingListPage />
          </RequireGroup>
        </ProtectedRoute>
      </AuthProvider>
    ),
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
