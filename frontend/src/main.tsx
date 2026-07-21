import { StrictMode, Suspense, lazy } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import './index.css'
import { initObservability } from './observability'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { RequireGroup } from './features/auth/RequireGroup'
import { LoadingSpinner } from './features/auth/LoadingSpinner'

// Route-level code splitting: each page ships as its own chunk so the
// initial bundle doesn't pay for e.g. @dnd-kit (shopping-list) just to
// render the login screen.
const LoginPage = lazy(() =>
  import('./features/auth/LoginPage').then((m) => ({ default: m.LoginPage })),
)
const AuthCallback = lazy(() =>
  import('./features/auth/AuthCallback').then((m) => ({ default: m.AuthCallback })),
)
const OnboardingPage = lazy(() =>
  import('./features/onboarding/OnboardingPage').then((m) => ({
    default: m.OnboardingPage,
  })),
)
const ListsPage = lazy(() =>
  import('./features/shopping-list/components/ListsPage').then((m) => ({
    default: m.ListsPage,
  })),
)
const ShoppingListPage = lazy(() =>
  import('./features/shopping-list/components/ShoppingListPage').then((m) => ({
    default: m.ShoppingListPage,
  })),
)

initObservability()

const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/lists" replace />,
  },
  {
    path: '/login',
    element: (
      <AuthProvider>
        <Suspense fallback={<LoadingSpinner />}>
          <LoginPage />
        </Suspense>
      </AuthProvider>
    ),
  },
  {
    path: '/auth/callback',
    element: (
      <AuthProvider>
        <Suspense fallback={<LoadingSpinner />}>
          <AuthCallback />
        </Suspense>
      </AuthProvider>
    ),
  },
  {
    path: '/welcome',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <Suspense fallback={<LoadingSpinner />}>
            <OnboardingPage />
          </Suspense>
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
            <Suspense fallback={<LoadingSpinner />}>
              <ListsPage />
            </Suspense>
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
            <Suspense fallback={<LoadingSpinner />}>
              <ShoppingListPage />
            </Suspense>
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
