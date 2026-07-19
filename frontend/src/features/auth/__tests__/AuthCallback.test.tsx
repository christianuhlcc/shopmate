import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as AuthContextModule from '../AuthContext'
import * as clientModule from '../../../api/client'
import { AuthCallback } from '../AuthCallback'

vi.mock('../AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof AuthContextModule>()
  return { ...actual, useAuth: vi.fn() }
})

vi.mock('../../../api/client', () => ({
  apiClient: { POST: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockUseAuth = vi.mocked(AuthContextModule.useAuth)
const mockApiClient = vi.mocked(clientModule.apiClient)

function renderCallback(search: string) {
  const setToken = vi.fn().mockResolvedValue(undefined)
  mockUseAuth.mockReturnValue({
    token: null,
    user: null,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    setToken,
  })

  render(
    <MemoryRouter initialEntries={[`/auth/callback${search}`]}>
      <Routes>
        <Route path="/auth/callback" element={<AuthCallback />} />
        <Route path="/lists" element={<div>Lists page</div>} />
        <Route path="/login" element={<div>Login page</div>} />
      </Routes>
    </MemoryRouter>,
  )

  return { setToken }
}

describe('AuthCallback', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows loading state initially', async () => {
    // Make the POST hang
    mockApiClient.POST = vi.fn(() => new Promise(() => {})) as typeof mockApiClient.POST
    renderCallback('?code=abc123')
    expect(screen.getByText(/signing you in/i)).toBeInTheDocument()
  })

  it('extracts code from URL and calls exchange endpoint', async () => {
    mockApiClient.POST = vi.fn().mockResolvedValue({ data: { token: 'jwt-abc' }, error: undefined }) as typeof mockApiClient.POST
    const { setToken } = renderCallback('?code=my-code')

    await waitFor(() => {
      expect(mockApiClient.POST).toHaveBeenCalledWith(
        '/auth/exchange',
        expect.objectContaining({ body: { code: 'my-code' } }),
      )
    })

    await waitFor(() => {
      expect(setToken).toHaveBeenCalledWith('jwt-abc')
    })
  })

  it('navigates to /lists on success', async () => {
    mockApiClient.POST = vi.fn().mockResolvedValue({ data: { token: 'jwt-abc' }, error: undefined }) as typeof mockApiClient.POST
    renderCallback('?code=my-code')

    await waitFor(() => {
      expect(screen.getByText('Lists page')).toBeInTheDocument()
    })
  })

  it('shows error when no code in URL', async () => {
    mockApiClient.POST = vi.fn() as typeof mockApiClient.POST
    renderCallback('')

    await waitFor(() => {
      expect(screen.getByText(/no authorization code/i)).toBeInTheDocument()
    })
    expect(screen.getByRole('link', { name: /back to sign-in/i })).toBeInTheDocument()
  })

  it('shows error when exchange fails', async () => {
    mockApiClient.POST = vi.fn().mockResolvedValue({ data: undefined, error: { message: 'bad' } }) as typeof mockApiClient.POST
    renderCallback('?code=bad-code')

    await waitFor(() => {
      expect(screen.getByText(/authentication failed/i)).toBeInTheDocument()
    })
  })
})
