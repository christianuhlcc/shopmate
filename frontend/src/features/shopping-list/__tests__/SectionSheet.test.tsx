import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SectionSheet } from '../components/SectionSheet'
import { SECTIONS } from '../utils/sections'

function renderSheet(overrides: Partial<Parameters<typeof SectionSheet>[0]> = {}) {
  const props = {
    currentSection: 'MOLKEREI_EIER',
    onSelect: vi.fn(),
    onClose: vi.fn(),
    ...overrides,
  }
  render(<SectionSheet {...props} />)
  return props
}

describe('SectionSheet', () => {
  it('renders all 14 sections', () => {
    renderSheet()
    for (const section of SECTIONS) {
      expect(screen.getByRole('button', { name: new RegExp(section.label, 'i') })).toBeInTheDocument()
    }
  })

  it('highlights the current section', () => {
    renderSheet({ currentSection: 'GETRAENKE' })
    const current = screen.getByRole('button', { name: /getränke/i })
    expect(current).toHaveAttribute('aria-current', 'true')
  })

  it('does not mark a non-current section as current', () => {
    renderSheet({ currentSection: 'GETRAENKE' })
    const other = screen.getByRole('button', { name: /^tiefkühl/i })
    expect(other).not.toHaveAttribute('aria-current')
  })

  it('selecting a section calls onSelect with its code', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderSheet()
    await user.click(screen.getByRole('button', { name: /obst & gemüse/i }))
    expect(onSelect).toHaveBeenCalledWith('OBST_GEMUESE')
  })

  it('clicking the backdrop closes the sheet', async () => {
    const user = userEvent.setup()
    const { onClose } = renderSheet()
    await user.click(screen.getByRole('dialog').parentElement!)
    expect(onClose).toHaveBeenCalled()
  })

  it('clicking inside the panel does not close the sheet', async () => {
    const user = userEvent.setup()
    const { onClose } = renderSheet()
    await user.click(screen.getByRole('dialog'))
    expect(onClose).not.toHaveBeenCalled()
  })

  it('pressing Escape closes the sheet', () => {
    const { onClose } = renderSheet()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})
