import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { ListsPage } from '../components/ListsPage'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), POST: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  POST: ReturnType<typeof vi.fn>
}

function makeList(id: string, name: string, memberCount = 1) {
  return {
    id,
    name,
    ownerId: 'owner-1',
    members: Array.from({ length: memberCount }, (_, i) => ({
      id: `u${i}`,
      email: `u${i}@example.com`,
      displayName: `User ${i}`,
    })),
    createdAt: '2026-01-01T00:00:00Z',
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/lists']}>
      <Routes>
        <Route path="/lists" element={<ListsPage />} />
        <Route path="/lists/:listId" element={<div>List detail</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedApi.GET.mockResolvedValue({ data: [makeList('l1', 'Groceries', 2)], error: undefined })
  mockedApi.POST.mockResolvedValue({ data: makeList('l2', 'Hardware'), error: undefined })
})

describe('ListsPage', () => {
  it('renders lists with member counts after loading', async () => {
    renderPage()
    expect(await screen.findByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('2 members')).toBeInTheDocument()
  })

  it('renders singular member label', async () => {
    mockedApi.GET.mockResolvedValue({ data: [makeList('l1', 'Solo', 1)], error: undefined })
    renderPage()
    expect(await screen.findByText('1 member')).toBeInTheDocument()
  })

  it('shows empty state when there are no lists', async () => {
    mockedApi.GET.mockResolvedValue({ data: [], error: undefined })
    renderPage()
    expect(await screen.findByText(/no lists yet/i)).toBeInTheDocument()
  })

  it('shows an error when loading fails', async () => {
    mockedApi.GET.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    renderPage()
    expect(await screen.findByText(/failed to load lists/i)).toBeInTheDocument()
  })

  it('shows an error when the request rejects', async () => {
    mockedApi.GET.mockRejectedValue(new Error('network'))
    renderPage()
    expect(await screen.findByText(/failed to load lists/i)).toBeInTheDocument()
  })

  it('navigates to a list when clicked', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(await screen.findByText('Groceries'))
    expect(screen.getByText('List detail')).toBeInTheDocument()
  })

  it('creates a new list and navigates to it', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Groceries')

    await user.click(screen.getByRole('button', { name: /new list/i }))
    await user.type(screen.getByPlaceholderText(/list name/i), 'Hardware')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/api/lists', { body: { name: 'Hardware' } }),
    )
    expect(await screen.findByText('List detail')).toBeInTheDocument()
  })

  it('cancel closes the create dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Groceries')

    await user.click(screen.getByRole('button', { name: /new list/i }))
    expect(screen.getByPlaceholderText(/list name/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(screen.queryByPlaceholderText(/list name/i)).not.toBeInTheDocument()
    expect(mockedApi.POST).not.toHaveBeenCalled()
  })

  it('does not create a list when creation fails', async () => {
    mockedApi.POST.mockResolvedValue({ data: undefined, error: { message: 'bad' } })
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Groceries')

    await user.click(screen.getByRole('button', { name: /new list/i }))
    await user.type(screen.getByPlaceholderText(/list name/i), 'Broken')
    await user.click(screen.getByRole('button', { name: /^create$/i }))

    await waitFor(() => expect(mockedApi.POST).toHaveBeenCalled())
    // Stays on the page, dialog remains open
    expect(screen.queryByText('List detail')).not.toBeInTheDocument()
    expect(screen.getByPlaceholderText(/list name/i)).toBeInTheDocument()
  })
})
