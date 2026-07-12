import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AddItemForm } from '../components/AddItemForm'

describe('AddItemForm', () => {
  it('renders input and add button', () => {
    render(<AddItemForm addItem={vi.fn()} />)
    expect(screen.getByPlaceholderText(/add an item/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add/i })).toBeInTheDocument()
  })

  it('calls addItem with name and clears input', async () => {
    const user = userEvent.setup()
    const addItem = vi.fn()
    render(<AddItemForm addItem={addItem} />)
    const input = screen.getByPlaceholderText(/add an item/i)
    await user.type(input, 'Milk')
    await user.click(screen.getByRole('button', { name: /add/i }))
    expect(addItem).toHaveBeenCalledWith('Milk')
    expect(input).toHaveValue('')
  })

  it('does not call addItem on empty input', async () => {
    const user = userEvent.setup()
    const addItem = vi.fn()
    render(<AddItemForm addItem={addItem} />)
    await user.click(screen.getByRole('button', { name: /add/i }))
    expect(addItem).not.toHaveBeenCalled()
  })

  it('does not call addItem on whitespace-only input', async () => {
    const user = userEvent.setup()
    const addItem = vi.fn()
    render(<AddItemForm addItem={addItem} />)
    const input = screen.getByPlaceholderText(/add an item/i)
    await user.type(input, '   ')
    await user.click(screen.getByRole('button', { name: /add/i }))
    expect(addItem).not.toHaveBeenCalled()
  })
})
