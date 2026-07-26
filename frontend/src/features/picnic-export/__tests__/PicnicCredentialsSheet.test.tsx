import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiClient } from '../../../api/client'
import { PicnicCredentialsSheet } from '../PicnicCredentialsSheet'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), PUT: vi.fn(), DELETE: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  PUT: ReturnType<typeof vi.fn>
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
    mockedApi.PUT.mockResolvedValue({ data: undefined, error: undefined })
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

    mockedApi.PUT.mockResolvedValue({ data: undefined, error: undefined })
    await user.click(screen.getByRole('button', { name: /link account/i }))
    expect(await screen.findByText('me@example.com')).toBeInTheDocument()
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
})
