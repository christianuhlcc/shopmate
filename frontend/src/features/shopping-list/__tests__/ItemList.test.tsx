import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ItemList } from '../components/ItemList'
import type { ShoppingItem } from '../utils/lwwMerge'

const USER_ID = '00000000-0000-0000-0000-000000000001'

function makeItem(id: string, name: string, sortKey: string, section = 'SONSTIGES'): ShoppingItem {
  return {
    id,
    listId: 'list-1',
    name: { value: name, timestamp: 100, modifiedBy: USER_ID },
    quantity: { value: '1', timestamp: 100, modifiedBy: USER_ID },
    checked: { value: false, timestamp: 100, modifiedBy: USER_ID },
    deleted: { value: false, timestamp: 100, modifiedBy: USER_ID },
    sortKey: { value: sortKey, timestamp: 100, modifiedBy: USER_ID },
    section: { value: section, timestamp: 100, modifiedBy: USER_ID },
  }
}

const handlers = () => ({
  checkItem: vi.fn(),
  updateItem: vi.fn(),
  deleteItem: vi.fn(),
  setSection: vi.fn(),
  moveItemTo: vi.fn(),
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

  it('renders section headings in walk order, not input order', () => {
    const items = [
      makeItem('1', 'Kaffee', 'a0', 'KAFFEE_TEE'),
      makeItem('2', 'Apfel', 'b0', 'OBST_GEMUESE'),
      makeItem('3', 'Milch', 'c0', 'MOLKEREI_EIER'),
    ]
    render(<ItemList items={items} {...handlers()} />)
    const headings = screen.getAllByRole('heading', { level: 2 })
    // Walk order: Obst & Gemüse (1) < Molkerei & Eier (3) < Kaffee & Tee (10)
    expect(headings.map((h) => h.textContent)).toEqual([
      'Obst & Gemüse',
      'Molkerei & Eier',
      'Kaffee & Tee',
    ])
  })

  it('hides section headings with no items', () => {
    const items = [makeItem('1', 'Kaffee', 'a0', 'KAFFEE_TEE')]
    render(<ItemList items={items} {...handlers()} />)
    expect(screen.getByRole('heading', { name: 'Kaffee & Tee' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Obst & Gemüse' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sonstiges' })).not.toBeInTheDocument()
  })

  it('keeps the checked panel flat and last, unaffected by section grouping', () => {
    const items = [
      makeItem('1', 'Kaffee', 'a0', 'KAFFEE_TEE'),
      {
        ...makeItem('2', 'Apfel', 'b0', 'OBST_GEMUESE'),
        checked: { value: true, timestamp: 200, modifiedBy: USER_ID },
      },
    ]
    render(<ItemList items={items} {...handlers()} />)
    const headings = screen.getAllByRole('heading', { level: 2 }).map((h) => h.textContent)
    // Only the unchecked section heading appears among h2s; "In the cart" is last and flat.
    expect(headings).toEqual(['Kaffee & Tee', 'In the cart · 1'])
  })
})
