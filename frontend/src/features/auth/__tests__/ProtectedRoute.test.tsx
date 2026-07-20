import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { ProtectedRoute } from '../ProtectedRoute'
import * as AuthContextModule from '../AuthContext'

// We mock useAuth to control token/isLoading state
vi.mock('../AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof AuthContextModule>()
  return { ...actual, useAuth: vi.fn() }
})

const mockUseAuth = vi.mocked(AuthContextModule.useAuth)

describe('ProtectedRoute', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redirects to /login when there is no token', () => {
    mockUseAuth.mockReturnValue({
      token: null,
      user: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      setToken: vi.fn(),
      refreshUser: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route
            path="/protected"
            element={
              <ProtectedRoute>
                <div>Secret content</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>Login page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Login page')).toBeInTheDocument()
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument()
  })

  it('renders children when token is present', () => {
    mockUseAuth.mockReturnValue({
      token: 'my-jwt-token',
      user: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
      setToken: vi.fn(),
      refreshUser: vi.fn(),
    })

    render(
      <MemoryRouter initialEntries={['/protected']}>
        <Routes>
          <Route
            path="/protected"
            element={
              <ProtectedRoute>
                <div>Secret content</div>
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>Login page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('Secret content')).toBeInTheDocument()
    expect(screen.queryByText('Login page')).not.toBeInTheDocument()
  })

  it('shows a spinner while loading', () => {
    mockUseAuth.mockReturnValue({
      token: null,
      user: null,
      isLoading: true,
      login: vi.fn(),
      logout: vi.fn(),
      setToken: vi.fn(),
      refreshUser: vi.fn(),
    })

    render(
      <MemoryRouter>
        <ProtectedRoute>
          <div>Secret content</div>
        </ProtectedRoute>
      </MemoryRouter>,
    )

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument()
  })
})
