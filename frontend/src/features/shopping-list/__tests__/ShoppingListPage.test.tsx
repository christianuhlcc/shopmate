import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { apiClient } from '../../../api/client'
import { useShoppingList } from '../hooks/useShoppingList'
import { ShoppingListPage } from '../components/ShoppingListPage'
import type { ShoppingItem } from '../utils/lwwMerge'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), POST: vi.fn() },
  setApiToken: vi.fn(),
}))

vi.mock('../hooks/useShoppingList', () => ({
  useShoppingList: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as { POST: ReturnType<typeof vi.fn> }
const mockedHook = vi.mocked(useShoppingList)

const USER_ID = '00000000-0000-0000-0000-000000000001'

function makeItem(id: string, name: string): ShoppingItem {
  return {
    id,
    listId: 'list-1',
    name: { value: name, timestamp: 100, modifiedBy: USER_ID },
    quantity: { value: '1', timestamp: 100, modifiedBy: USER_ID },
    checked: { value: false, timestamp: 100, modifiedBy: USER_ID },
    deleted: { value: false, timestamp: 100, modifiedBy: USER_ID },
    sortKey: { value: 'a0', timestamp: 100, modifiedBy: USER_ID },
    section: { value: 'SONSTIGES', timestamp: 100, modifiedBy: USER_ID },
  }
}

function hookState(overrides: Partial<ReturnType<typeof useShoppingList>> = {}) {
  return {
    items: [makeItem('i1', 'Milk')],
    listName: 'Groceries',
    error: null,
    isLoading: false,
    addItem: vi.fn(),
    updateItem: vi.fn(),
    checkItem: vi.fn(),
    deleteItem: vi.fn(),
    moveItem: vi.fn(),
    ...overrides,
  }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/lists/list-1']}>
      <Routes>
        <Route path="/lists/:listId" element={<ShoppingListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedHook.mockReturnValue(hookState())
  mockedApi.POST.mockResolvedValue({ data: undefined, error: undefined })
})

describe('ShoppingListPage', () => {
  it('shows a loading skeleton while loading', () => {
    mockedHook.mockReturnValue(hookState({ isLoading: true }))
    renderPage()
    expect(screen.getByRole('status', { name: /loading list/i })).toBeInTheDocument()
  })

  it('shows the error state', () => {
    mockedHook.mockReturnValue(hookState({ error: 'Failed to load shopping list.' }))
    renderPage()
    expect(screen.getByText('Failed to load shopping list.')).toBeInTheDocument()
  })

  it('renders list name and items', () => {
    renderPage()
    expect(screen.getByText('Groceries')).toBeInTheDocument()
    expect(screen.getByText('Milk')).toBeInTheDocument()
  })

  it('shares the list with an email', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByRole('button', { name: /^share$/i }))
    await user.type(screen.getByPlaceholderText(/email address/i), 'bob@example.com')
    const shareButtons = screen.getAllByRole('button', { name: /^share$/i })
    await user.click(shareButtons[shareButtons.length - 1]) // modal submit

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith('/lists/{listId}/members', {
        params: { path: { listId: 'list-1' } },
        body: { email: 'bob@example.com' },
      }),
    )
    // Modal closes on success
    await waitFor(() =>
      expect(screen.queryByPlaceholderText(/email address/i)).not.toBeInTheDocument(),
    )
  })

  it('shows an error when sharing fails', async () => {
    mockedApi.POST.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByRole('button', { name: /^share$/i }))
    await user.type(screen.getByPlaceholderText(/email address/i), 'bob@example.com')
    const shareButtons = screen.getAllByRole('button', { name: /^share$/i })
    await user.click(shareButtons[shareButtons.length - 1]) // modal submit

    expect(await screen.findByText(/could not share list/i)).toBeInTheDocument()
    expect(screen.getByPlaceholderText(/email address/i)).toBeInTheDocument()
  })

  it('cancel closes the share dialog', async () => {
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByRole('button', { name: /^share$/i }))
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(screen.queryByPlaceholderText(/email address/i)).not.toBeInTheDocument()
    expect(mockedApi.POST).not.toHaveBeenCalled()
  })
})
