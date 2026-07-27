import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiClient } from '../../../api/client'
import { PicnicCredentialsSheet } from '../PicnicCredentialsSheet'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), PUT: vi.fn(), POST: vi.fn(), DELETE: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  PUT: ReturnType<typeof vi.fn>
  POST: ReturnType<typeof vi.fn>
  DELETE: ReturnType<typeof vi.fn>
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PicnicCredentialsSheet', () => {
  it('shows a loading state, then the linked view when already linked', async () => {
    mockedApi.GET.mockResolvedValue({
      data: { linked: true, email: 'x@y.com' },
      error: undefined,
    })
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    expect(screen.getByRole('status', { name: /loading picnic account status/i })).toBeInTheDocument()

    expect(await screen.findByText('x@y.com')).toBeInTheDocument()
    expect(screen.getByText(/linked for grocery export/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /unlink/i })).toBeInTheDocument()
  })

  it('renders the form when not linked', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument()
    const passwordInput = screen.getByLabelText(/password/i)
    expect(passwordInput).toHaveAttribute('type', 'password')
    expect(passwordInput).toHaveAttribute('autoComplete', 'current-password')
    expect(screen.getByRole('button', { name: /link account/i })).toBeDisabled()
  })

  it('renders a generic error banner when the status fetch fails', async () => {
    mockedApi.GET.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)
    expect(await screen.findByText(/could not load your picnic account status/i)).toBeInTheDocument()
  })

  it('submitting the form calls PUT with the entered credentials and shows the linked view on success', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    mockedApi.PUT.mockResolvedValue({ data: { status: 'LINKED' }, error: undefined })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    await screen.findByLabelText(/email/i)
    await user.type(screen.getByLabelText(/email/i), 'me@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter2')
    await user.click(screen.getByRole('button', { name: /link account/i }))

    await waitFor(() =>
      expect(mockedApi.PUT).toHaveBeenCalledWith('/users/me/picnic-credentials', {
        body: { email: 'me@example.com', password: 'hunter2' },
      }),
    )

    expect(await screen.findByText('me@example.com')).toBeInTheDocument()
    expect(screen.getByText(/linked for grocery export/i)).toBeInTheDocument()
  })

  it('submitting with a PICNIC_LOGIN_FAILED error shows the specific inline message and stays on the form', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    mockedApi.PUT.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_LOGIN_FAILED', message: 'bad login' },
    })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    await screen.findByLabelText(/email/i)
    await user.type(screen.getByLabelText(/email/i), 'me@example.com')
    await user.type(screen.getByLabelText(/password/i), 'wrongpass')
    await user.click(screen.getByRole('button', { name: /link account/i }))

    expect(
      await screen.findByText(/picnic rejected these credentials/i),
    ).toBeInTheDocument()
    // Form is still usable/resubmittable.
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /link account/i })).not.toBeDisabled()

    mockedApi.PUT.mockResolvedValue({ data: { status: 'LINKED' }, error: undefined })
    await user.click(screen.getByRole('button', { name: /link account/i }))
    expect(await screen.findByText('me@example.com')).toBeInTheDocument()
  })

  it('submitting while Picnic is down shows the outage message, not the wrong-password one', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    mockedApi.PUT.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_UNAVAILABLE', message: 'down' },
    })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    await screen.findByLabelText(/email/i)
    await user.type(screen.getByLabelText(/email/i), 'me@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter2')
    await user.click(screen.getByRole('button', { name: /link account/i }))

    expect(
      await screen.findByText(/picnic isn't available right now/i),
    ).toBeInTheDocument()
    expect(screen.queryByText(/check your email and password/i)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /link account/i })).not.toBeDisabled()
  })

  it('submitting with a generic error shows a generic message', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    mockedApi.PUT.mockResolvedValue({
      data: undefined,
      error: { code: 'SOMETHING_ELSE', message: 'oops' },
    })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    await screen.findByLabelText(/email/i)
    await user.type(screen.getByLabelText(/email/i), 'me@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter2')
    await user.click(screen.getByRole('button', { name: /link account/i }))

    expect(await screen.findByText(/something went wrong/i)).toBeInTheDocument()
  })

  it('clicking Unlink calls DELETE and returns to the form view on success', async () => {
    mockedApi.GET.mockResolvedValue({
      data: { linked: true, email: 'x@y.com' },
      error: undefined,
    })
    mockedApi.DELETE.mockResolvedValue({ data: undefined, error: undefined })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    await screen.findByText('x@y.com')
    await user.click(screen.getByRole('button', { name: /unlink/i }))

    await waitFor(() => expect(mockedApi.DELETE).toHaveBeenCalledWith('/users/me/picnic-credentials'))
    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument()
    expect(screen.queryByText('x@y.com')).not.toBeInTheDocument()
  })

  it('clicking the backdrop closes the sheet', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<PicnicCredentialsSheet onClose={onClose} />)
    await screen.findByLabelText(/email/i)
    await user.click(screen.getByRole('dialog').parentElement!)
    expect(onClose).toHaveBeenCalled()
  })

  it('pressing Escape closes the sheet', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    const onClose = vi.fn()
    render(<PicnicCredentialsSheet onClose={onClose} />)
    await screen.findByLabelText(/email/i)
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })

  // --- second factor ---------------------------------------------------------

  async function linkAndReachCodeStep(onLinked: () => void = vi.fn()) {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    mockedApi.PUT.mockResolvedValue({
      data: { status: 'PENDING_SECOND_FACTOR' },
      error: undefined,
    })
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} onLinked={onLinked} />)

    await screen.findByLabelText(/email/i)
    await user.type(screen.getByLabelText(/email/i), 'me@example.com')
    await user.type(screen.getByLabelText(/password/i), 'hunter2')
    await user.click(screen.getByRole('button', { name: /link account/i }))
    await screen.findByLabelText(/sms code/i)
    return user
  }

  it('a PENDING_SECOND_FACTOR link switches to the code step instead of claiming success', async () => {
    // Picnic hands out a key even when it still wants a code, so treating this as
    // "linked" would show a success screen for an account that cannot export.
    await linkAndReachCodeStep()

    expect(screen.getByLabelText(/sms code/i)).toBeInTheDocument()
    expect(screen.queryByText(/linked for grocery export/i)).not.toBeInTheDocument()
    // The password field is gone — the flow moved on and should not hold it.
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
  })

  it('verifying the code completes linking', async () => {
    const user = await linkAndReachCodeStep()
    mockedApi.POST.mockResolvedValue({ data: undefined, error: undefined })

    await user.type(screen.getByLabelText(/sms code/i), '252000')
    await user.click(screen.getByRole('button', { name: /verify code/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/users/me/picnic-credentials/2fa/verify', {
        body: { code: '252000' },
      }),
    )
    expect(await screen.findByText(/linked for grocery export/i)).toBeInTheDocument()
  })

  it('a rejected code keeps the user on the code step so they can retry', async () => {
    // The pending link survives server-side; bouncing back to the password form
    // would make the user redo work they already did correctly.
    const user = await linkAndReachCodeStep()
    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_LOGIN_FAILED', message: 'bad code' },
    })

    await user.type(screen.getByLabelText(/sms code/i), '000000')
    await user.click(screen.getByRole('button', { name: /verify code/i }))

    expect(await screen.findByText(/that code wasn't accepted/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/sms code/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
  })

  it('can request a new code', async () => {
    const user = await linkAndReachCodeStep()
    mockedApi.POST.mockResolvedValue({ data: undefined, error: undefined })

    await user.click(screen.getByRole('button', { name: /send a new code/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/users/me/picnic-credentials/2fa/send'),
    )
    expect(await screen.findByText(/we sent a new code/i)).toBeInTheDocument()
  })

  it('surfaces a failure to resend rather than silently doing nothing', async () => {
    const user = await linkAndReachCodeStep()
    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_UNAVAILABLE', message: 'down' },
    })

    await user.click(screen.getByRole('button', { name: /send a new code/i }))

    expect(await screen.findByText(/picnic isn't available right now/i)).toBeInTheDocument()
  })

  it('resumes at the code step when the sheet reopens on a pending link', async () => {
    // The half-finished link is stored, so reopening must not ask for the password again.
    mockedApi.GET.mockResolvedValue({
      data: { linked: false, email: 'me@example.com', status: 'PENDING_SECOND_FACTOR' },
      error: undefined,
    })
    render(<PicnicCredentialsSheet onClose={vi.fn()} />)

    expect(await screen.findByLabelText(/sms code/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
  })

  // --- getting back out of the sheet ------------------------------------------

  it('offers a way back to exporting once linked, not just Unlink', async () => {
    // Reported as a dead end: after verifying, the only exits were the backdrop and
    // Escape, so the user had no visible way to continue the export they started.
    mockedApi.GET.mockResolvedValue({
      data: { linked: true, email: 'x@y.com', status: 'LINKED' },
      error: undefined,
    })
    const onLinked = vi.fn()
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={vi.fn()} onLinked={onLinked} />)

    await user.click(await screen.findByRole('button', { name: /export this list/i }))

    expect(onLinked).toHaveBeenCalled()
  })

  it('offers the export continuation straight after verifying the code', async () => {
    const user = await linkAndReachCodeStep()
    mockedApi.POST.mockResolvedValue({ data: undefined, error: undefined })

    await user.type(screen.getByLabelText(/sms code/i), '252000')
    await user.click(screen.getByRole('button', { name: /verify code/i }))

    expect(await screen.findByRole('button', { name: /export this list/i })).toBeInTheDocument()
  })

  it('has an explicit close control', async () => {
    mockedApi.GET.mockResolvedValue({ data: { linked: false }, error: undefined })
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<PicnicCredentialsSheet onClose={onClose} />)

    await screen.findByLabelText(/email/i)
    await user.click(screen.getByRole('button', { name: /close/i }))

    expect(onClose).toHaveBeenCalled()
  })
})
