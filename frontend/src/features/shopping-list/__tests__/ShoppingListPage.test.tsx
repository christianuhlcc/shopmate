import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useShoppingList } from '../hooks/useShoppingList'
import { ShoppingListPage } from '../components/ShoppingListPage'
import type { ShoppingItem } from '../utils/lwwMerge'

vi.mock('../hooks/useShoppingList', () => ({
  useShoppingList: vi.fn(),
}))

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
    setSection: vi.fn(),
    moveItemTo: vi.fn(),
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
})
