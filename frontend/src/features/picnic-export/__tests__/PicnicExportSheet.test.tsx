import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { apiClient } from '../../../api/client'
import { PicnicExportSheet } from '../PicnicExportSheet'

vi.mock('../../../api/client', () => ({
  apiClient: { GET: vi.fn(), POST: vi.fn() },
  setApiToken: vi.fn(),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  POST: ReturnType<typeof vi.fn>
}

const suggestions = {
  items: [
    {
      itemId: 'item-1',
      itemName: 'Milch',
      suggestions: [
        {
          id: 'art-1',
          name: 'Bio Vollmilch',
          priceCents: 129,
          unit: '1L',
          imageUrl: 'https://example.com/milch.png',
        },
        { id: 'art-2', name: 'Haltbare Milch', priceCents: 99 },
      ],
    },
    {
      itemId: 'item-2',
      itemName: 'Bananen',
      suggestions: [],
    },
  ],
}

function makeResult(overrides: Partial<{ added: number; skipped: number; failures: unknown[] }> = {}) {
  return {
    added: 1,
    skipped: 1,
    failures: [],
    ...overrides,
  }
}

async function renderPicking() {
  mockedApi.POST.mockResolvedValueOnce({ data: suggestions, error: undefined })
  const onClose = vi.fn()
  const onNeedsCredentials = vi.fn()
  render(
    <PicnicExportSheet listId="list-1" onClose={onClose} onNeedsCredentials={onNeedsCredentials} />,
  )
  await screen.findByText('Milch')
  return { onClose, onNeedsCredentials }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PicnicExportSheet', () => {
  it('loads suggestions and defaults each item to its first suggestion', async () => {
    await renderPicking()

    expect(
      (screen.getByRole('radio', { name: /bio vollmilch/i }) as HTMLInputElement).checked,
    ).toBe(true)
    expect(
      (screen.getByRole('radio', { name: /haltbare milch/i }) as HTMLInputElement).checked,
    ).toBe(false)
    expect(mockedApi.POST).toHaveBeenCalledWith('/lists/{listId}/picnic/suggestions', {
      params: { path: { listId: 'list-1' } },
    })
  })

  it('shows the credentials-needed prompt and calls onNeedsCredentials, not onClose', async () => {
    mockedApi.POST.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'PICNIC_CREDENTIALS_MISSING', message: 'nope', timestamp: '2026-07-26T00:00:00Z' },
    })
    const onClose = vi.fn()
    const onNeedsCredentials = vi.fn()
    const user = userEvent.setup()
    render(
      <PicnicExportSheet listId="list-1" onClose={onClose} onNeedsCredentials={onNeedsCredentials} />,
    )

    expect(await screen.findByText(/link a picnic account to export this list/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /link picnic account/i }))
    expect(onNeedsCredentials).toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('renders a product image when a suggestion has an imageUrl', async () => {
    mockedApi.POST.mockResolvedValueOnce({ data: suggestions, error: undefined })
    const { container } = render(
      <PicnicExportSheet listId="list-1" onClose={vi.fn()} onNeedsCredentials={vi.fn()} />,
    )
    await screen.findByText('Milch')

    // alt="" is intentional (decorative product thumbnail) — that gives the <img> an
    // implicit "presentation" role in the accessibility tree, so getByRole('img')
    // can't find it; query the DOM directly instead.
    const image = container.querySelector('img')
    expect(image).not.toBeNull()
    expect(image?.src).toBe('https://example.com/milch.png')
  })

  it('shows the generic error banner when the suggestions request itself rejects', async () => {
    mockedApi.POST.mockRejectedValueOnce(new Error('network down'))
    render(<PicnicExportSheet listId="list-1" onClose={vi.fn()} onNeedsCredentials={vi.fn()} />)

    expect(
      await screen.findByText(/picnic isn't available right now — try again later/i),
    ).toBeInTheDocument()
  })

  it('shows a generic error banner for an unexpected suggestions failure', async () => {
    mockedApi.POST.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'PICNIC_UNAVAILABLE', message: 'down', timestamp: '2026-07-26T00:00:00Z' },
    })
    render(<PicnicExportSheet listId="list-1" onClose={vi.fn()} onNeedsCredentials={vi.fn()} />)

    expect(
      await screen.findByText(/picnic isn't available right now — try again later/i),
    ).toBeInTheDocument()
  })

  it('an item with no suggestions shows "No matches found" and is skipped automatically', async () => {
    await renderPicking()
    expect(screen.getByText(/no matches found/i)).toBeInTheDocument()

    mockedApi.POST.mockResolvedValueOnce({ data: makeResult(), error: undefined })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenLastCalledWith('/lists/{listId}/picnic/export', {
        params: { path: { listId: 'list-1' } },
        body: {
          selections: [
            { itemId: 'item-1', articleId: 'art-1' },
            { itemId: 'item-2', articleId: undefined },
          ],
        },
      }),
    )
  })

  it('changing the radio selection changes what is submitted', async () => {
    await renderPicking()
    const user = userEvent.setup()

    await user.click(screen.getByRole('radio', { name: /haltbare milch/i }))

    mockedApi.POST.mockResolvedValueOnce({ data: makeResult(), error: undefined })
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenLastCalledWith('/lists/{listId}/picnic/export', {
        params: { path: { listId: 'list-1' } },
        body: {
          selections: [
            { itemId: 'item-1', articleId: 'art-2' },
            { itemId: 'item-2', articleId: undefined },
          ],
        },
      }),
    )
  })

  it('selecting "skip" on an item omits its articleId from the submitted selection', async () => {
    await renderPicking()
    const user = userEvent.setup()

    await user.click(screen.getByRole('radio', { name: /skip this item/i, hidden: false }))

    mockedApi.POST.mockResolvedValueOnce({ data: makeResult(), error: undefined })
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    await waitFor(() => {
      const lastCall = mockedApi.POST.mock.calls[mockedApi.POST.mock.calls.length - 1]
      expect(lastCall[1].body.selections).toContainEqual({ itemId: 'item-1', articleId: undefined })
    })
  })

  it('shows the added/skipped summary on a successful export', async () => {
    await renderPicking()
    mockedApi.POST.mockResolvedValueOnce({
      data: makeResult({ added: 3, skipped: 2 }),
      error: undefined,
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    expect(await screen.findByText(/added 3, skipped 2/i)).toBeInTheDocument()
  })

  it('renders failures under a "Couldn\'t add" heading when present', async () => {
    await renderPicking()
    mockedApi.POST.mockResolvedValueOnce({
      data: makeResult({
        added: 1,
        skipped: 0,
        failures: [{ itemId: 'item-1', reason: 'Out of stock' }],
      }),
      error: undefined,
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    expect(await screen.findByText(/couldn.t add/i)).toBeInTheDocument()
    expect(screen.getByText(/milch/i)).toBeInTheDocument()
    expect(screen.getByText(/out of stock/i)).toBeInTheDocument()
  })

  it('falls back to the credentials-missing prompt when the export call itself fails that way', async () => {
    await renderPicking()
    mockedApi.POST.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'PICNIC_CREDENTIALS_MISSING', message: 'nope', timestamp: '2026-07-26T00:00:00Z' },
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    expect(await screen.findByText(/link a picnic account to export this list/i)).toBeInTheDocument()
  })

  it('falls back to the generic error banner when the export call fails generically', async () => {
    await renderPicking()
    mockedApi.POST.mockResolvedValueOnce({
      data: undefined,
      error: { code: 'PICNIC_UNAVAILABLE', message: 'down', timestamp: '2026-07-26T00:00:00Z' },
    })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /confirm export/i }))

    expect(
      await screen.findByText(/picnic isn't available right now — try again later/i),
    ).toBeInTheDocument()
  })

  it('clicking Done in the result state calls onClose', async () => {
    mockedApi.POST.mockResolvedValueOnce({ data: suggestions, error: undefined })
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<PicnicExportSheet listId="list-1" onClose={onClose} onNeedsCredentials={vi.fn()} />)
    await screen.findByText('Milch')

    mockedApi.POST.mockResolvedValueOnce({ data: makeResult(), error: undefined })
    await user.click(screen.getByRole('button', { name: /confirm export/i }))
    await screen.findByText(/added 1, skipped 1/i)

    await user.click(screen.getByRole('button', { name: /done/i }))
    expect(onClose).toHaveBeenCalled()
  })

  it('clicking the backdrop closes the sheet', async () => {
    const onClose = vi.fn()
    mockedApi.POST.mockResolvedValueOnce({ data: suggestions, error: undefined })
    const user = userEvent.setup()
    render(<PicnicExportSheet listId="list-1" onClose={onClose} onNeedsCredentials={vi.fn()} />)
    await screen.findByText('Milch')

    await user.click(screen.getByRole('dialog').parentElement!)
    expect(onClose).toHaveBeenCalled()
  })

  it('pressing Escape closes the sheet', async () => {
    const onClose = vi.fn()
    mockedApi.POST.mockResolvedValueOnce({ data: suggestions, error: undefined })
    render(<PicnicExportSheet listId="list-1" onClose={onClose} onNeedsCredentials={vi.fn()} />)
    await screen.findByText('Milch')

    fireEvent.keyDown(window, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })
})
