import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { RequireGroup } from '../RequireGroup'
import * as AuthContextModule from '../AuthContext'

// We mock useAuth to control user/isLoading state, same idiom as
// ProtectedRoute.test.tsx.
vi.mock('../AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof AuthContextModule>()
  return { ...actual, useAuth: vi.fn() }
})

const mockUseAuth = vi.mocked(AuthContextModule.useAuth)

function baseAuth(overrides: Partial<ReturnType<typeof mockUseAuth>>) {
  return {
    token: 'jwt',
    user: null,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    setToken: vi.fn(),
    refreshUser: vi.fn(),
    ...overrides,
  }
}

function renderGuard() {
  return render(
    <MemoryRouter initialEntries={['/lists']}>
      <Routes>
        <Route
          path="/lists"
          element={
            <RequireGroup>
              <div>Lists content</div>
            </RequireGroup>
          }
        />
        <Route path="/welcome" element={<div>Welcome page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireGroup', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redirects a group-less user to /welcome', () => {
    mockUseAuth.mockReturnValue(
      baseAuth({ user: { id: 'u1', email: 'a@b.com', displayName: 'A', group: null } }),
    )

    renderGuard()

    expect(screen.getByText('Welcome page')).toBeInTheDocument()
    expect(screen.queryByText('Lists content')).not.toBeInTheDocument()
  })

  it('passes a grouped user through', () => {
    mockUseAuth.mockReturnValue(
      baseAuth({
        user: {
          id: 'u1',
          email: 'a@b.com',
          displayName: 'A',
          group: { id: 'g1', name: 'The Smiths' },
        },
      }),
    )

    renderGuard()

    expect(screen.getByText('Lists content')).toBeInTheDocument()
    expect(screen.queryByText('Welcome page')).not.toBeInTheDocument()
  })

  it('shows a spinner while loading', () => {
    mockUseAuth.mockReturnValue(baseAuth({ isLoading: true }))

    renderGuard()

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.queryByText('Lists content')).not.toBeInTheDocument()
  })
})
