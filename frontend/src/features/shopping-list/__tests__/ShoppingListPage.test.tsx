import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { useShoppingList } from '../hooks/useShoppingList'
import { ShoppingListPage } from '../components/ShoppingListPage'
import type { ShoppingItem } from '../utils/lwwMerge'

vi.mock('../hooks/useShoppingList', () => ({
  useShoppingList: vi.fn(),
}))

// The export sheet fetches suggestions and the credentials sheet fetches
// link status via apiClient as soon as they mount — mocked out here so this
// test only proves the header wiring, not the sheets' own behavior (already
// covered by PicnicExportSheet.test.tsx / PicnicCredentialsSheet.test.tsx).
vi.mock('../../picnic-export/PicnicExportSheet', () => ({
  PicnicExportSheet: ({
    onClose,
    onNeedsCredentials,
  }: {
    onClose: () => void
    onNeedsCredentials: () => void
  }) => (
    <div role="dialog" aria-label="Export to Picnic sheet">
      <button onClick={onClose}>Close export sheet</button>
      <button onClick={onNeedsCredentials}>Report missing credentials</button>
    </div>
  ),
}))
vi.mock('../../picnic-export/PicnicCredentialsSheet', () => ({
  PicnicCredentialsSheet: ({ onClose }: { onClose: () => void }) => (
    <div role="dialog" aria-label="Picnic credentials sheet">
      <button onClick={onClose}>Close credentials sheet</button>
    </div>
  ),
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

  it('opens the Picnic export sheet from the header button', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(screen.queryByRole('dialog', { name: /export to picnic sheet/i })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /export to picnic/i }))
    expect(screen.getByRole('dialog', { name: /export to picnic sheet/i })).toBeInTheDocument()
  })

  it('swaps the export sheet for the credentials sheet when Picnic credentials are missing', async () => {
    // PICNIC_CREDENTIALS_MISSING must route the user to linking rather than show a bare
    // error (plan §4). The sheets each handle their own half; this proves the page wires
    // the handoff between them.
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /export to picnic/i }))
    await user.click(screen.getByRole('button', { name: /report missing credentials/i }))

    expect(screen.getByRole('dialog', { name: /picnic credentials sheet/i })).toBeInTheDocument()
    expect(
      screen.queryByRole('dialog', { name: /export to picnic sheet/i }),
    ).not.toBeInTheDocument()
  })

  it('closes each Picnic sheet back to the plain list', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(screen.getByRole('button', { name: /export to picnic/i }))
    await user.click(screen.getByRole('button', { name: /close export sheet/i }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /export to picnic/i }))
    await user.click(screen.getByRole('button', { name: /report missing credentials/i }))
    await user.click(screen.getByRole('button', { name: /close credentials sheet/i }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})
