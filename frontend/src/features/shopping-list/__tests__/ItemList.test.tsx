import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ItemList } from '../components/ItemList'
import type { ShoppingItem } from '../utils/lwwMerge'

const USER_ID = '00000000-0000-0000-0000-000000000001'

function makeItem(id: string, name: string, sortKey: string): ShoppingItem {
  return {
    id,
    listId: 'list-1',
    name: { value: name, timestamp: 100, modifiedBy: USER_ID },
    quantity: { value: '1', timestamp: 100, modifiedBy: USER_ID },
    checked: { value: false, timestamp: 100, modifiedBy: USER_ID },
    deleted: { value: false, timestamp: 100, modifiedBy: USER_ID },
    sortKey: { value: sortKey, timestamp: 100, modifiedBy: USER_ID },
    section: { value: 'SONSTIGES', timestamp: 100, modifiedBy: USER_ID },
  }
}

const handlers = () => ({
  checkItem: vi.fn(),
  updateItem: vi.fn(),
  deleteItem: vi.fn(),
  moveItem: vi.fn(),
})

describe('ItemList', () => {
  it('shows empty state when there are no items', () => {
    render(<ItemList items={[]} {...handlers()} />)
    expect(screen.getByText(/no items yet/i)).toBeInTheDocument()
  })

  it('renders a row per item', () => {
    const items = [makeItem('1', 'Milk', 'a0'), makeItem('2', 'Eggs', 'b0')]
    render(<ItemList items={items} {...handlers()} />)
    expect(screen.getByText('Milk')).toBeInTheDocument()
    expect(screen.getByText('Eggs')).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('groups checked items under "In the cart"', () => {
    const checkedItem = {
      ...makeItem('2', 'Eggs', 'b0'),
      checked: { value: true, timestamp: 200, modifiedBy: USER_ID },
    }
    const items = [makeItem('1', 'Milk', 'a0'), checkedItem]
    render(<ItemList items={items} {...handlers()} />)
    expect(screen.getByText(/in the cart/i)).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
  })

  it('renders only the checked section when everything is checked', () => {
    const items = [
      {
        ...makeItem('1', 'Milk', 'a0'),
        checked: { value: true, timestamp: 200, modifiedBy: USER_ID },
      },
    ]
    render(<ItemList items={items} {...handlers()} />)
    expect(screen.getByText(/in the cart/i)).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(1)
  })
})
