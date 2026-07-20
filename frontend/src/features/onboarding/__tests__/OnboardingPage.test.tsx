import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { OnboardingPage } from '../OnboardingPage'
import * as AuthContextModule from '../../auth/AuthContext'
import * as clientModule from '../../../api/client'

vi.mock('../../auth/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof AuthContextModule>()
  return { ...actual, useAuth: vi.fn() }
})

vi.mock('../../../api/client', () => ({
  apiClient: { POST: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockUseAuth = vi.mocked(AuthContextModule.useAuth)
const mockApiClient = vi.mocked(clientModule.apiClient)

function renderOnboarding(user: { id: string; email: string; displayName: string; group?: { id: string; name: string } | null } | null = null) {
  const refreshUser = vi.fn().mockResolvedValue(undefined)
  mockUseAuth.mockReturnValue({
    token: 'jwt',
    user,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    setToken: vi.fn(),
    refreshUser,
  })

  render(
    <MemoryRouter initialEntries={['/welcome']}>
      <Routes>
        <Route path="/welcome" element={<OnboardingPage />} />
        <Route path="/lists" element={<div>Lists page</div>} />
      </Routes>
    </MemoryRouter>,
  )

  return { refreshUser }
}

describe('OnboardingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('redirects to /lists immediately if the user already has a group', () => {
    renderOnboarding({ id: 'u1', email: 'a@b.com', displayName: 'A', group: { id: 'g1', name: 'The Smiths' } })

    expect(screen.getByText('Lists page')).toBeInTheDocument()
  })

  it('redeems a JOIN_GROUP code and navigates to /lists on success', async () => {
    const user = userEvent.setup()
    mockApiClient.POST = vi.fn().mockResolvedValue({
      data: { id: 'u1', email: 'a@b.com', displayName: 'A', group: { id: 'g1', name: 'The Smiths' } },
      error: undefined,
    }) as typeof mockApiClient.POST
    const { refreshUser } = renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'AB3DE9FG')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    await waitFor(() => {
      expect(mockApiClient.POST).toHaveBeenCalledWith(
        '/invites/redeem',
        expect.objectContaining({ body: { code: 'AB3DE9FG' } }),
      )
    })
    await waitFor(() => expect(refreshUser).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByText('Lists page')).toBeInTheDocument())
  })

  it('reveals the household-name field on GROUP_NAME_REQUIRED and resubmits the same code without asking the user to re-enter it', async () => {
    const user = userEvent.setup()
    const post = vi.fn()
    post.mockResolvedValueOnce({ data: undefined, error: { code: 'GROUP_NAME_REQUIRED' } })
    post.mockResolvedValueOnce({
      data: { id: 'u1', email: 'a@b.com', displayName: 'A', group: { id: 'g2', name: 'The Newmans' } },
      error: undefined,
    })
    mockApiClient.POST = post as typeof mockApiClient.POST
    const { refreshUser } = renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'NEWGRP01')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    // The name step appears; the code field is gone, so the user cannot
    // (and need not) re-enter the code.
    expect(await screen.findByLabelText(/household name/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/invite code/i)).not.toBeInTheDocument()

    await user.type(screen.getByLabelText(/household name/i), 'The Newmans')
    await user.click(screen.getByRole('button', { name: /create household/i }))

    await waitFor(() => {
      expect(post).toHaveBeenNthCalledWith(
        2,
        '/invites/redeem',
        expect.objectContaining({ body: { code: 'NEWGRP01', groupName: 'The Newmans' } }),
      )
    })
    await waitFor(() => expect(refreshUser).toHaveBeenCalled())
    await waitFor(() => expect(screen.getByText('Lists page')).toBeInTheDocument())
  })

  it.each([
    ['INVITE_INVALID', /invite code isn't valid/i],
    ['INVITE_EXPIRED', /expired/i],
    ['ALREADY_IN_GROUP', /already part of a household/i],
  ])('shows a friendly message for %s', async (code, expectedMessage) => {
    const user = userEvent.setup()
    mockApiClient.POST = vi.fn().mockResolvedValue({
      data: undefined,
      error: { code },
    }) as typeof mockApiClient.POST
    renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'BADCODE1')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(expectedMessage)
    // Still on the code step, not stuck in a broken state.
    expect(screen.getByLabelText(/invite code/i)).toBeInTheDocument()
  })

  it('shows a friendly error if resubmitting with a group name fails', async () => {
    const user = userEvent.setup()
    const post = vi.fn()
    post.mockResolvedValueOnce({ data: undefined, error: { code: 'GROUP_NAME_REQUIRED' } })
    post.mockResolvedValueOnce({ data: undefined, error: { code: 'INVITE_EXPIRED' } })
    mockApiClient.POST = post as typeof mockApiClient.POST
    renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'NEWGRP01')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    await user.type(await screen.findByLabelText(/household name/i), 'The Newmans')
    await user.click(screen.getByRole('button', { name: /create household/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/expired/i)
    // Still on the name step, code was never re-requested.
    expect(screen.getByLabelText(/household name/i)).toBeInTheDocument()
  })

  it('falls back to a generic message for an unrecognized error code', async () => {
    const user = userEvent.setup()
    mockApiClient.POST = vi.fn().mockResolvedValue({
      data: undefined,
      error: { code: 'SOMETHING_UNEXPECTED' },
    }) as typeof mockApiClient.POST
    renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'BADCODE1')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/something went wrong/i)
  })

  it('lets the user back out of the name step to try a different code', async () => {
    const user = userEvent.setup()
    mockApiClient.POST = vi.fn().mockResolvedValue({
      data: undefined,
      error: { code: 'GROUP_NAME_REQUIRED' },
    }) as typeof mockApiClient.POST
    renderOnboarding()

    await user.type(screen.getByLabelText(/invite code/i), 'NEWGRP01')
    await user.click(screen.getByRole('button', { name: /continue/i }))

    await screen.findByLabelText(/household name/i)
    await user.click(screen.getByRole('button', { name: /use a different code/i }))

    expect(await screen.findByLabelText(/invite code/i)).toHaveValue('')
  })
})
