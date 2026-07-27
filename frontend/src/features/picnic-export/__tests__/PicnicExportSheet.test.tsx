import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
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

const ITEMS = [
  { id: 'item-1', name: 'Milch' },
  { id: 'item-2', name: 'Bananen' },
]

const MILCH = {
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
}

const BANANEN = { itemId: 'item-2', itemName: 'Bananen', suggestions: [] }

const SUGGESTIONS_PATH = '/lists/{listId}/picnic/suggestions/{itemId}'
const EXPORT_PATH = '/lists/{listId}/picnic/export'

function makeResult(overrides: Partial<{ added: number; skipped: number; failures: unknown[] }> = {}) {
  return { added: 1, skipped: 1, failures: [], ...overrides }
}

/** Routes mocked calls by path/itemId, so prefetching does not depend on call order. */
function mockSuggestions(byItem: Record<string, unknown>, exportResult?: unknown) {
  mockedApi.POST.mockImplementation(
    (path: string, opts?: { params?: { path?: { itemId?: string } } }) => {
      if (path === EXPORT_PATH) {
        return Promise.resolve({ data: exportResult ?? makeResult(), error: undefined })
      }
      const entry = byItem[opts?.params?.path?.itemId ?? '']
      if (entry === undefined) {
        return Promise.resolve({
          data: undefined,
          error: { code: 'PICNIC_UNAVAILABLE', message: 'down' },
        })
      }
      return Promise.resolve({ data: entry, error: undefined })
    },
  )
}

async function renderStepper(exportResult?: unknown) {
  mockSuggestions({ 'item-1': MILCH, 'item-2': BANANEN }, exportResult)
  const onClose = vi.fn()
  const onNeedsCredentials = vi.fn()
  render(
    <PicnicExportSheet
      listId="list-1"
      items={ITEMS}
      onClose={onClose}
      onNeedsCredentials={onNeedsCredentials}
    />,
  )
  await screen.findByText(/bio vollmilch/i)
  return { onClose, onNeedsCredentials, user: userEvent.setup() }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('PicnicExportSheet', () => {
  it('fetches only the first item up front, not the whole list', async () => {
    // The point of stepping: a 30-item list must not cost 30 Picnic searches before
    // the user can make their first choice.
    mockSuggestions({ 'item-1': MILCH, 'item-2': BANANEN })
    render(
      <PicnicExportSheet
        listId="list-1"
        items={ITEMS}
        onClose={vi.fn()}
        onNeedsCredentials={vi.fn()}
      />,
    )

    expect(screen.getByRole('status', { name: /loading suggestions/i })).toBeInTheDocument()
    await screen.findByText(/bio vollmilch/i)

    expect(mockedApi.POST).toHaveBeenCalledWith(SUGGESTIONS_PATH, {
      params: { path: { listId: 'list-1', itemId: 'item-1' } },
    })
    expect(screen.getByText(/item 1 of 2/i)).toBeInTheDocument()
  })

  it('prefetches the next item while the user is still deciding', async () => {
    // This is what hides Picnic's ~2-3 s render: by the time Next is pressed, the
    // next item is already in hand.
    await renderStepper()

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith(SUGGESTIONS_PATH, {
        params: { path: { listId: 'list-1', itemId: 'item-2' } },
      }),
    )
  })

  it('steps forward and back without refetching', async () => {
    const { user } = await renderStepper()
    await waitFor(() => expect(mockedApi.POST).toHaveBeenCalledTimes(2))

    await user.click(screen.getByRole('button', { name: /next/i }))
    expect(await screen.findByText(/item 2 of 2/i)).toBeInTheDocument()
    expect(screen.getByText(/no matches found/i)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /back/i }))
    expect(await screen.findByText(/item 1 of 2/i)).toBeInTheDocument()

    // Still two calls: revisiting an item must not pay for it again.
    expect(mockedApi.POST).toHaveBeenCalledTimes(2)
  })

  it('defaults to the top match and sends selections on confirm', async () => {
    const { user } = await renderStepper()

    await user.click(screen.getByRole('button', { name: /next/i }))
    await user.click(await screen.findByRole('button', { name: /confirm export/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith(EXPORT_PATH, {
        params: { path: { listId: 'list-1' } },
        body: {
          selections: [
            { itemId: 'item-1', articleId: 'art-1' },
            // Nothing came back for Bananen, so it exports as a skip.
            { itemId: 'item-2', articleId: undefined },
          ],
        },
      }),
    )
    expect(await screen.findByText(/added 1, skipped 1/i)).toBeInTheDocument()
  })

  it('honours a different pick', async () => {
    const { user } = await renderStepper()

    await user.click(screen.getByRole('radio', { name: /haltbare milch/i }))
    await user.click(screen.getByRole('button', { name: /next/i }))
    await user.click(await screen.findByRole('button', { name: /confirm export/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith(
        EXPORT_PATH,
        expect.objectContaining({
          body: {
            selections: [
              { itemId: 'item-1', articleId: 'art-2' },
              { itemId: 'item-2', articleId: undefined },
            ],
          },
        }),
      ),
    )
  })

  it('lets the user skip an item explicitly', async () => {
    const { user } = await renderStepper()

    await user.click(screen.getByRole('radio', { name: /skip this item/i }))
    await user.click(screen.getByRole('button', { name: /next/i }))
    await user.click(await screen.findByRole('button', { name: /confirm export/i }))

    await waitFor(() =>
      expect(mockedApi.POST).toHaveBeenCalledWith(
        EXPORT_PATH,
        expect.objectContaining({
          body: {
            selections: [
              { itemId: 'item-1', articleId: undefined },
              { itemId: 'item-2', articleId: undefined },
            ],
          },
        }),
      ),
    )
  })

  it('renders a product image when a suggestion has one', async () => {
    await renderStepper()

    const img = document.querySelector('img')
    expect(img?.getAttribute('src')).toBe('https://example.com/milch.png')
  })

  it('a failed item is retryable on its own without killing the sheet', async () => {
    // Per-item failure isolation is a direct benefit of stepping — one bad search
    // used to fail the whole list.
    let attempts = 0
    mockedApi.POST.mockImplementation(
      (path: string, opts?: { params?: { path?: { itemId?: string } } }) => {
        if (path === EXPORT_PATH) return Promise.resolve({ data: makeResult(), error: undefined })
        if (opts?.params?.path?.itemId === 'item-1') {
          attempts += 1
          if (attempts === 1) {
            return Promise.resolve({
              data: undefined,
              error: { code: 'PICNIC_UNAVAILABLE', message: 'down' },
            })
          }
          return Promise.resolve({ data: MILCH, error: undefined })
        }
        return Promise.resolve({ data: BANANEN, error: undefined })
      },
    )
    const user = userEvent.setup()
    render(
      <PicnicExportSheet
        listId="list-1"
        items={ITEMS}
        onClose={vi.fn()}
        onNeedsCredentials={vi.fn()}
      />,
    )

    expect(await screen.findByText(/picnic isn't available right now/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /try this item again/i }))

    expect(await screen.findByText(/bio vollmilch/i)).toBeInTheDocument()
  })

  it('shows the credentials-needed prompt and calls onNeedsCredentials, not onClose', async () => {
    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_CREDENTIALS_MISSING', message: 'nope' },
    })
    const onClose = vi.fn()
    const onNeedsCredentials = vi.fn()
    const user = userEvent.setup()
    render(
      <PicnicExportSheet
        listId="list-1"
        items={ITEMS}
        onClose={onClose}
        onNeedsCredentials={onNeedsCredentials}
      />,
    )

    expect(await screen.findByText(/link a picnic account to export this list/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /link picnic account/i }))
    expect(onNeedsCredentials).toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('routes an expired session to re-linking rather than showing a retryable error', async () => {
    // Picnic's 2FA cannot be re-cleared without the user, so an expired session is a
    // dead end here — offering "try again" would loop them forever.
    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_SESSION_EXPIRED', message: 'gone' },
    })
    const onNeedsCredentials = vi.fn()
    const user = userEvent.setup()
    render(
      <PicnicExportSheet
        listId="list-1"
        items={ITEMS}
        onClose={vi.fn()}
        onNeedsCredentials={onNeedsCredentials}
      />,
    )

    expect(await screen.findByText(/your picnic session has expired/i)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /link picnic account/i }))
    expect(onNeedsCredentials).toHaveBeenCalled()
  })

  it('sends a half-finished link back to the credentials sheet to finish 2FA', async () => {
    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_SECOND_FACTOR_REQUIRED', message: 'pending' },
    })
    render(
      <PicnicExportSheet
        listId="list-1"
        items={ITEMS}
        onClose={vi.fn()}
        onNeedsCredentials={vi.fn()}
      />,
    )

    expect(await screen.findByText(/still needs the sms code/i)).toBeInTheDocument()
  })

  it('surfaces a failed export without crashing the sheet', async () => {
    const { user } = await renderStepper()
    await user.click(screen.getByRole('button', { name: /next/i }))

    mockedApi.POST.mockResolvedValue({
      data: undefined,
      error: { code: 'PICNIC_UNAVAILABLE', message: 'down' },
    })
    await user.click(await screen.findByRole('button', { name: /confirm export/i }))

    expect(await screen.findByText(/picnic isn't available right now/i)).toBeInTheDocument()
  })

  it('lists per-item failures in the result summary', async () => {
    const { user } = await renderStepper(
      makeResult({ added: 0, skipped: 1, failures: [{ itemId: 'item-1', reason: 'Out of stock' }] }),
    )

    await user.click(screen.getByRole('button', { name: /next/i }))
    await user.click(await screen.findByRole('button', { name: /confirm export/i }))

    expect(await screen.findByText(/couldn't add/i)).toBeInTheDocument()
    expect(screen.getByText(/Milch — Out of stock/)).toBeInTheDocument()
  })

  it('closes from the header control', async () => {
    const { onClose, user } = await renderStepper()

    await user.click(screen.getByRole('button', { name: /close/i }))

    expect(onClose).toHaveBeenCalled()
  })

  it('says so when there is nothing to export', () => {
    render(
      <PicnicExportSheet
        listId="list-1"
        items={[]}
        onClose={vi.fn()}
        onNeedsCredentials={vi.fn()}
      />,
    )

    expect(screen.getByText(/nothing to export/i)).toBeInTheDocument()
    expect(mockedApi.POST).not.toHaveBeenCalled()
  })

  it('closes on Escape', async () => {
    const { onClose } = await renderStepper()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))

    expect(onClose).toHaveBeenCalled()
  })
})
