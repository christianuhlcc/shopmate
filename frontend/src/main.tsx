import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom'
import './index.css'
import { AuthProvider } from './features/auth/AuthContext'
import { ProtectedRoute } from './features/auth/ProtectedRoute'
import { LoginPage } from './features/auth/LoginPage'
import { AuthCallback } from './features/auth/AuthCallback'
import { ListsPage } from './features/shopping-list/components/ListsPage'
import { ShoppingListPage } from './features/shopping-list/components/ShoppingListPage'

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
    path: '/lists',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <ListsPage />
        </ProtectedRoute>
      </AuthProvider>
    ),
  },
  {
    path: '/lists/:listId',
    element: (
      <AuthProvider>
        <ProtectedRoute>
          <ShoppingListPage />
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
