import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { AuthProvider, useAuth } from '../AuthContext'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as { GET: ReturnType<typeof vi.fn> }

const PROFILE = {
  id: 'u1',
  email: 'alice@example.com',
  displayName: 'Alice',
}

function Consumer() {
  const { token, user, isLoading, logout, setToken } = useAuth()
  return (
    <div>
      <div data-testid="loading">{String(isLoading)}</div>
      <div data-testid="token">{token ?? 'none'}</div>
      <div data-testid="user">{user?.email ?? 'none'}</div>
      <button onClick={logout}>logout</button>
      <button onClick={() => void setToken('fresh-jwt')}>set-token</button>
    </div>
  )
}

function renderWithProvider() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route
          path="/"
          element={
            <AuthProvider>
              <Consumer />
            </AuthProvider>
          }
        />
        <Route path="/login" element={<div>Login page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  mockedApi.GET.mockResolvedValue({ data: PROFILE, error: undefined })
})

describe('AuthProvider', () => {
  it('starts unauthenticated without a stored token', () => {
    renderWithProvider()
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    expect(screen.getByTestId('token')).toHaveTextContent('none')
    expect(screen.getByTestId('user')).toHaveTextContent('none')
    expect(mockedApi.GET).not.toHaveBeenCalled()
  })

  it('loads the user profile when a token is stored', async () => {
    localStorage.setItem('auth_token', 'stored-jwt')
    renderWithProvider()
    expect(screen.getByTestId('loading')).toHaveTextContent('true')
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('alice@example.com'))
    expect(screen.getByTestId('loading')).toHaveTextContent('false')
    expect(screen.getByTestId('token')).toHaveTextContent('stored-jwt')
  })

  it('clears an invalid stored token', async () => {
    localStorage.setItem('auth_token', 'bad-jwt')
    mockedApi.GET.mockResolvedValue({ data: undefined, error: { message: 'unauthorized' } })
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('none'))
    expect(localStorage.getItem('auth_token')).toBeNull()
  })

  it('clears the stored token when the profile request rejects', async () => {
    localStorage.setItem('auth_token', 'bad-jwt')
    mockedApi.GET.mockRejectedValue(new Error('network'))
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('token')).toHaveTextContent('none'))
    expect(localStorage.getItem('auth_token')).toBeNull()
  })

  it('setToken stores the token and fetches the profile', async () => {
    const user = userEvent.setup()
    renderWithProvider()
    await user.click(screen.getByRole('button', { name: 'set-token' }))
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('alice@example.com'))
    expect(localStorage.getItem('auth_token')).toBe('fresh-jwt')
    expect(screen.getByTestId('token')).toHaveTextContent('fresh-jwt')
  })

  it('logout clears state and navigates to /login', async () => {
    localStorage.setItem('auth_token', 'stored-jwt')
    const user = userEvent.setup()
    renderWithProvider()
    await waitFor(() => expect(screen.getByTestId('user')).toHaveTextContent('alice@example.com'))
    await user.click(screen.getByRole('button', { name: 'logout' }))
    expect(localStorage.getItem('auth_token')).toBeNull()
    expect(screen.getByText('Login page')).toBeInTheDocument()
  })
})

describe('useAuth', () => {
  it('throws when used outside AuthProvider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Consumer />)).toThrow(/useAuth must be used inside/)
    spy.mockRestore()
  })
})
