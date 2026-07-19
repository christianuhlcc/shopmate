import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ItemRow } from '../components/ItemRow'
import type { ShoppingItem } from '../utils/lwwMerge'

const USER_ID = '00000000-0000-0000-0000-000000000001'

function makeItem(overrides: Partial<ShoppingItem> = {}): ShoppingItem {
  return {
    id: 'item-1',
    listId: 'list-1',
    name: { value: 'Milk', timestamp: 100, modifiedBy: USER_ID },
    quantity: { value: '1', timestamp: 100, modifiedBy: USER_ID },
    checked: { value: false, timestamp: 100, modifiedBy: USER_ID },
    deleted: { value: false, timestamp: 100, modifiedBy: USER_ID },
    sortKey: { value: 'a0', timestamp: 100, modifiedBy: USER_ID },
    section: { value: 'SONSTIGES', timestamp: 100, modifiedBy: USER_ID },
    ...overrides,
  }
}

function renderRow(item: ShoppingItem, overrides: Partial<Parameters<typeof ItemRow>[0]> = {}) {
  const props = {
    item,
    checkItem: vi.fn(),
    updateItem: vi.fn(),
    deleteItem: vi.fn(),
    moveItem: vi.fn(),
    ...overrides,
  }
  render(<ItemRow {...props} />)
  return props
}

describe('ItemRow', () => {
  it('renders item name', () => {
    renderRow(makeItem())
    expect(screen.getByText('Milk')).toBeInTheDocument()
  })

  it('clicking checkbox calls checkItem with toggled value', async () => {
    const user = userEvent.setup()
    const { checkItem } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /^check item$/i }))
    expect(checkItem).toHaveBeenCalledWith('item-1', true)
  })

  it('clicking checkbox on a checked item calls checkItem with false', async () => {
    const user = userEvent.setup()
    const item = makeItem({ checked: { value: true, timestamp: 200, modifiedBy: USER_ID } })
    const { checkItem } = renderRow(item)
    await user.click(screen.getByRole('button', { name: /uncheck item/i }))
    expect(checkItem).toHaveBeenCalledWith('item-1', false)
  })

  it('clicking delete button calls deleteItem', async () => {
    const user = userEvent.setup()
    const { deleteItem } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /delete item/i }))
    expect(deleteItem).toHaveBeenCalledWith('item-1')
  })

  it('clicking name enters edit mode', async () => {
    const user = userEvent.setup()
    renderRow(makeItem())
    await user.click(screen.getByText('Milk'))
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('blurring edit input calls updateItem with the new name', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByText('Milk'))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Oat Milk')
    await user.tab() // triggers blur
    expect(updateItem).toHaveBeenCalledWith('item-1', 'NAME', 'Oat Milk')
  })

  it('does not call updateItem when the name is unchanged', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByText('Milk'))
    await user.tab() // blur without editing
    expect(updateItem).not.toHaveBeenCalled()
  })

  it('escape cancels editing without calling updateItem', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByText('Milk'))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Something else')
    await user.keyboard('{Escape}')
    expect(updateItem).not.toHaveBeenCalled()
    expect(screen.getByText('Milk')).toBeInTheDocument()
  })

  it('enter commits the edit', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByText('Milk'))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, 'Bread{Enter}')
    expect(updateItem).toHaveBeenCalledWith('item-1', 'NAME', 'Bread')
  })

  it('renders checked item with line-through style', () => {
    const item = makeItem({ checked: { value: true, timestamp: 200, modifiedBy: USER_ID } })
    renderRow(item)
    expect(screen.getByText('Milk')).toHaveClass('line-through')
  })

  it('shows quantity badge when quantity is not 1', () => {
    const item = makeItem({ quantity: { value: '3', timestamp: 100, modifiedBy: USER_ID } })
    renderRow(item)
    expect(screen.getByText('3')).toBeInTheDocument()
  })
})
