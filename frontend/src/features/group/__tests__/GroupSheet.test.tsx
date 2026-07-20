import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiClient } from '../../../api/client'
import { GroupSheet } from '../GroupSheet'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), POST: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  POST: ReturnType<typeof vi.fn>
}

const group = {
  id: 'group-1',
  name: 'The Millers',
  createdAt: '2026-01-01T00:00:00Z',
  members: [
    { id: 'u1', email: 'alice@example.com', displayName: 'Alice' },
    { id: 'u2', email: 'bob@example.com', displayName: 'Bob' },
  ],
}

function makeInvite(type: 'JOIN_GROUP' | 'NEW_GROUP') {
  return {
    code: type === 'JOIN_GROUP' ? 'ABCD2345' : 'WXYZ6789',
    type,
    expiresAt: '2026-07-27T12:00:00Z',
  }
}

const writeText = vi.fn().mockResolvedValue(undefined)

beforeEach(() => {
  vi.clearAllMocks()
  mockedApi.GET.mockResolvedValue({ data: group, error: undefined })
  mockedApi.POST.mockImplementation((_url: string, options: { body: { type: 'JOIN_GROUP' | 'NEW_GROUP' } }) =>
    Promise.resolve({ data: makeInvite(options.body.type), error: undefined }),
  )
  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText },
    configurable: true,
  })
})

describe('GroupSheet', () => {
  it('renders the group name and member list', async () => {
    render(<GroupSheet onClose={vi.fn()} />)
    expect(await screen.findByText('The Millers')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('alice@example.com')).toBeInTheDocument()
    expect(screen.getByText('Bob')).toBeInTheDocument()
    expect(screen.getByText('bob@example.com')).toBeInTheDocument()
  })

  it('creating a JOIN_GROUP invite shows its code and expiry', async () => {
    const user = userEvent.setup()
    render(<GroupSheet onClose={vi.fn()} />)
    await screen.findByText('The Millers')

    await user.click(screen.getByRole('button', { name: /invite to this group/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/invites', { body: { type: 'JOIN_GROUP' } }),
    )
    expect(await screen.findByText('ABCD2345')).toBeInTheDocument()
    expect(screen.getByText(/expires/i)).toBeInTheDocument()
  })

  it('creating a NEW_GROUP invite shows its code and expiry', async () => {
    const user = userEvent.setup()
    render(<GroupSheet onClose={vi.fn()} />)
    await screen.findByText('The Millers')

    await user.click(screen.getByRole('button', { name: /invite for a new group/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/invites', { body: { type: 'NEW_GROUP' } }),
    )
    expect(await screen.findByText('WXYZ6789')).toBeInTheDocument()
    expect(screen.getByText(/expires/i)).toBeInTheDocument()
  })

  it('copy action calls the clipboard API with the code', async () => {
    const user = userEvent.setup()
    // userEvent.setup() installs its own navigator.clipboard stub, clobbering the
    // one from beforeEach — so re-install the spy after setup, not before.
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    render(<GroupSheet onClose={vi.fn()} />)
    await screen.findByText('The Millers')

    await user.click(screen.getByRole('button', { name: /invite to this group/i }))
    await screen.findByText('ABCD2345')

    await user.click(screen.getByRole('button', { name: /copy code/i }))
    expect(writeText).toHaveBeenCalledWith('ABCD2345')
    expect(await screen.findByRole('button', { name: /copied/i })).toBeInTheDocument()
  })

  it('renders an error state when the group fetch fails', async () => {
    mockedApi.GET.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    render(<GroupSheet onClose={vi.fn()} />)
    expect(await screen.findByText(/could not load your group/i)).toBeInTheDocument()
  })

  it('renders an error state when the group fetch rejects', async () => {
    mockedApi.GET.mockRejectedValue(new Error('network'))
    render(<GroupSheet onClose={vi.fn()} />)
    expect(await screen.findByText(/could not load your group/i)).toBeInTheDocument()
  })

  it('shows an error when invite creation fails', async () => {
    mockedApi.POST.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    const user = userEvent.setup()
    render(<GroupSheet onClose={vi.fn()} />)
    await screen.findByText('The Millers')

    await user.click(screen.getByRole('button', { name: /invite to this group/i }))
    expect(await screen.findByText(/could not create an invite code/i)).toBeInTheDocument()
  })

  it('clicking the backdrop closes the sheet', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<GroupSheet onClose={onClose} />)
    await screen.findByText('The Millers')
    await user.click(screen.getByRole('dialog').parentElement!)
    expect(onClose).toHaveBeenCalled()
  })

  it('clicking inside the panel does not close the sheet', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(<GroupSheet onClose={onClose} />)
    await screen.findByText('The Millers')
    await user.click(screen.getByRole('dialog'))
    expect(onClose).not.toHaveBeenCalled()
  })

  it('pressing Escape closes the sheet', async () => {
    const onClose = vi.fn()
    render(<GroupSheet onClose={onClose} />)
    await screen.findByText('The Millers')
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})
