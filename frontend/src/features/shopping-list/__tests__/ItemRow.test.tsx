import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DndContext } from '@dnd-kit/core'
import { SortableContext } from '@dnd-kit/sortable'
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
    setSection: vi.fn(),
    ...overrides,
  }
  render(<ItemRow {...props} />)
  return props
}

/** Renders inside a real DndContext + SortableContext so the KeyboardSensor's handle wiring is exercised. */
function renderSortableRow(
  item: ShoppingItem,
  overrides: Partial<Parameters<typeof ItemRow>[0]> = {},
  onDragEnd: (event: unknown) => void = () => {},
) {
  const props = {
    item,
    checkItem: vi.fn(),
    updateItem: vi.fn(),
    deleteItem: vi.fn(),
    setSection: vi.fn(),
    ...overrides,
  }
  render(
    <DndContext onDragEnd={onDragEnd}>
      <SortableContext items={[item.id, 'other-item']}>
        <ul>
          <ItemRow {...props} />
        </ul>
      </SortableContext>
    </DndContext>,
  )
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

  it('shows the quantity chip with the default value of 1', () => {
    renderRow(makeItem())
    expect(screen.getByRole('button', { name: /change quantity, currently 1/i })).toBeInTheDocument()
  })

  it('clicking quantity enters edit mode', async () => {
    const user = userEvent.setup()
    const item = makeItem({ quantity: { value: '3', timestamp: 100, modifiedBy: USER_ID } })
    renderRow(item)
    await user.click(screen.getByRole('button', { name: /change quantity, currently 3/i }))
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('blurring quantity edit input calls updateItem with the new quantity', async () => {
    const user = userEvent.setup()
    const item = makeItem({ quantity: { value: '3', timestamp: 100, modifiedBy: USER_ID } })
    const { updateItem } = renderRow(item)
    await user.click(screen.getByRole('button', { name: /change quantity, currently 3/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '500 g')
    await user.tab() // triggers blur
    expect(updateItem).toHaveBeenCalledWith('item-1', 'QUANTITY', '500 g')
  })

  it('does not call updateItem when the quantity is unchanged', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /change quantity, currently 1/i }))
    await user.tab() // blur without editing
    expect(updateItem).not.toHaveBeenCalled()
  })

  it('escape cancels quantity editing without calling updateItem', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /change quantity, currently 1/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '6')
    await user.keyboard('{Escape}')
    expect(updateItem).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /change quantity, currently 1/i })).toBeInTheDocument()
  })

  it('enter commits the quantity edit', async () => {
    const user = userEvent.setup()
    const { updateItem } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /change quantity, currently 1/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.type(input, '6{Enter}')
    expect(updateItem).toHaveBeenCalledWith('item-1', 'QUANTITY', '6')
  })

  it('clearing the quantity commits it back to the default of 1', async () => {
    const user = userEvent.setup()
    const item = makeItem({ quantity: { value: '3', timestamp: 100, modifiedBy: USER_ID } })
    const { updateItem } = renderRow(item)
    await user.click(screen.getByRole('button', { name: /change quantity, currently 3/i }))
    const input = screen.getByRole('textbox')
    await user.clear(input)
    await user.tab()
    expect(updateItem).toHaveBeenCalledWith('item-1', 'QUANTITY', '1')
  })

  it('shows a drag handle by default', () => {
    renderRow(makeItem())
    expect(screen.getByRole('button', { name: /reorder milk/i })).toBeInTheDocument()
  })

  it('omits the drag handle when draggable is false (checked panel rows)', () => {
    renderRow(makeItem(), { draggable: false })
    expect(screen.queryByRole('button', { name: /reorder/i })).not.toBeInTheDocument()
  })

  it('shows the section chip with the current section label', () => {
    const item = makeItem({ section: { value: 'GETRAENKE', timestamp: 100, modifiedBy: USER_ID } })
    renderRow(item)
    expect(screen.getByRole('button', { name: /change section, currently getränke/i })).toBeInTheDocument()
  })

  it('falls back to Sonstiges label for an unknown section code', () => {
    const item = makeItem({ section: { value: 'NOT_A_CODE', timestamp: 100, modifiedBy: USER_ID } })
    renderRow(item)
    expect(screen.getByRole('button', { name: /change section, currently sonstiges/i })).toBeInTheDocument()
  })

  it('opens the SectionSheet when the section chip is tapped, and closes on select', async () => {
    const user = userEvent.setup()
    const { setSection } = renderRow(makeItem())
    await user.click(screen.getByRole('button', { name: /change section/i }))
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /obst & gemüse/i }))
    expect(setSection).toHaveBeenCalledWith('item-1', 'OBST_GEMUESE')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  describe('drag handle wiring (keyboard sensor)', () => {
    it('gives the handle sortable a11y attributes', () => {
      renderSortableRow(makeItem())
      const handle = screen.getByRole('button', { name: /reorder milk/i })
      expect(handle).toHaveAttribute('aria-roledescription', 'sortable')
      expect(handle).toHaveAttribute('tabindex', '0')
    })

    it('activating the keyboard sensor (Space) marks the handle as pressed/dragging', () => {
      renderSortableRow(makeItem())
      const handle = screen.getByRole('button', { name: /reorder milk/i })
      handle.focus()
      fireEvent.keyDown(handle, { code: 'Space' })
      expect(handle).toHaveAttribute('aria-pressed', 'true')
      // End the drag (drop) so the sensor cleans up its listeners.
      fireEvent.keyDown(handle, { code: 'Space' })
    })

    it('does not wire keyboard drag attributes when draggable is false', () => {
      renderRow(makeItem(), { draggable: false })
      expect(screen.queryByRole('button', { name: /reorder/i })).not.toBeInTheDocument()
    })
  })
})
