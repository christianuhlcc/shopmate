import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { apiClient } from '../../../api/client'
import { useShoppingList } from '../hooks/useShoppingList'
import type { ItemChangeEvent } from '../utils/lwwMerge'

const USER_ID = '00000000-0000-0000-0000-0000000000aa'
const OTHER_USER = '00000000-0000-0000-0000-0000000000bb'
const LIST_ID = 'list-1'

const mocks = vi.hoisted(() => ({
  user: { id: '00000000-0000-0000-0000-0000000000aa' } as { id: string } | null,
}))

vi.mock('../../../api/client', () => ({
  apiClient: {
    GET: vi.fn(),
    POST: vi.fn(),
    PATCH: vi.fn(),
    DELETE: vi.fn(),
  },
  setApiToken: vi.fn(),
}))

vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ user: mocks.user }),
}))

const mockedApi = vi.mocked(apiClient) as unknown as {
  GET: ReturnType<typeof vi.fn>
  POST: ReturnType<typeof vi.fn>
  PATCH: ReturnType<typeof vi.fn>
  DELETE: ReturnType<typeof vi.fn>
}

/** Minimal EventSource stand-in that records instances and lets tests emit events. */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  closed = false
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  private listeners: Record<string, ((evt: MessageEvent) => void)[]> = {}

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, cb: (evt: MessageEvent) => void) {
    ;(this.listeners[type] ??= []).push(cb)
  }

  close() {
    this.closed = true
  }

  emit(type: string, data: string) {
    for (const cb of this.listeners[type] ?? []) {
      cb({ data } as MessageEvent)
    }
  }
}

vi.stubGlobal('EventSource', FakeEventSource)

function field<T>(value: T, timestamp: number, modifiedBy: string = OTHER_USER) {
  return { value, timestamp, modifiedBy }
}

function serverItem(overrides: Record<string, unknown> = {}) {
  return {
    id: 'item-1',
    listId: LIST_ID,
    name: field('Milk', 100),
    quantity: field('1', 100),
    checked: field(false, 100),
    deleted: field(false, 100),
    sortKey: field('b0', 100),
    section: field('SONSTIGES', 100),
    ...overrides,
  }
}

function changeEvent(overrides: Partial<ItemChangeEvent> = {}): ItemChangeEvent {
  return {
    itemId: 'item-1',
    listId: LIST_ID,
    field: 'CHECKED',
    value: 'true',
    timestamp: 150,
    modifiedBy: OTHER_USER,
    ...overrides,
  }
}

async function renderLoadedHook() {
  const rendered = renderHook(() => useShoppingList(LIST_ID))
  await waitFor(() => expect(rendered.result.current.isLoading).toBe(false))
  await waitFor(() => expect(FakeEventSource.instances.length).toBe(1))
  return rendered
}

const flush = () =>
  act(async () => {
    await Promise.resolve()
    await Promise.resolve()
    await Promise.resolve()
  })

beforeEach(() => {
  vi.clearAllMocks()
  FakeEventSource.instances = []
  mocks.user = { id: USER_ID }

  mockedApi.GET.mockResolvedValue({
    data: { id: LIST_ID, name: 'Groceries', items: [serverItem()] },
    error: undefined,
  })
  mockedApi.POST.mockImplementation((path: string) => {
    if (String(path).includes('sse-token')) {
      return Promise.resolve({ data: { token: 'sse-tok' }, error: undefined })
    }
    return Promise.resolve({ data: serverItem(), error: undefined })
  })
  mockedApi.PATCH.mockResolvedValue({ data: serverItem(), error: undefined })
  mockedApi.DELETE.mockResolvedValue({ data: undefined, error: undefined })
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useShoppingList — initial load', () => {
  it('loads list name and items', async () => {
    const { result } = await renderLoadedHook()
    expect(result.current.listName).toBe('Groceries')
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0].name.value).toBe('Milk')
    expect(result.current.items[0].section.value).toBe('SONSTIGES')
  })

  it('sets error when the API returns an error', async () => {
    mockedApi.GET.mockResolvedValue({ data: undefined, error: { message: 'nope' } })
    const { result } = renderHook(() => useShoppingList(LIST_ID))
    await waitFor(() => expect(result.current.error).toBe('Failed to load shopping list.'))
  })

  it('sets error when the request rejects', async () => {
    mockedApi.GET.mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useShoppingList(LIST_ID))
    await waitFor(() => expect(result.current.error).toBe('Failed to load shopping list.'))
  })
})

describe('useShoppingList — SSE events', () => {
  it('applies item-change events to state', async () => {
    const { result } = await renderLoadedHook()
    const es = FakeEventSource.instances[0]
    act(() => {
      es.emit('item-change', JSON.stringify(changeEvent({ field: 'NAME', value: 'Oat Milk', timestamp: 200 })))
    })
    expect(result.current.items[0].name.value).toBe('Oat Milk')
  })

  it('ignores malformed events', async () => {
    const { result } = await renderLoadedHook()
    const es = FakeEventSource.instances[0]
    act(() => {
      es.emit('item-change', '{not json')
    })
    expect(result.current.items[0].name.value).toBe('Milk')
  })
})

describe('useShoppingList — optimistic updates never fabricate timestamps', () => {
  it('checkItem changes only the value; timestamp and modifiedBy stay server-assigned', async () => {
    let resolvePatch!: (v: unknown) => void
    mockedApi.PATCH.mockImplementation(() => new Promise((res) => (resolvePatch = res)))
    const { result } = await renderLoadedHook()

    act(() => {
      void result.current.checkItem('item-1', true)
    })

    // Optimistic: value flipped, but the LWW metadata is untouched
    expect(result.current.items[0].checked).toEqual({
      value: true,
      timestamp: 100,
      modifiedBy: OTHER_USER,
    })

    // Server response (authoritative, higher timestamp) reconciles state
    resolvePatch({
      data: serverItem({ checked: field(true, 999, USER_ID) }),
      error: undefined,
    })
    await waitFor(() => expect(result.current.items[0].checked.timestamp).toBe(999))
  })

  it('a later authoritative SSE event beats the optimistic value (no client-clock advantage)', async () => {
    mockedApi.DELETE.mockImplementation(() => new Promise(() => {}))
    const { result } = await renderLoadedHook()
    const es = FakeEventSource.instances[0]

    await act(async () => {
      void result.current.deleteItem('item-1')
    })
    // Optimistic tombstone hides the item
    expect(result.current.items).toHaveLength(0)

    // A genuine later server event (ts 150 > preserved ts 100) must win.
    // With a fabricated Date.now() timestamp this event would lose.
    act(() => {
      es.emit('item-change', JSON.stringify(changeEvent({ field: 'DELETED', value: 'false', timestamp: 150 })))
    })
    expect(result.current.items).toHaveLength(1)
  })

  it('moveItemTo within the same section changes only the sortKey value with a single PATCH; timestamp preserved', async () => {
    mockedApi.GET.mockResolvedValue({
      data: {
        id: LIST_ID,
        name: 'Groceries',
        items: [
          serverItem(),
          serverItem({ id: 'item-2', name: field('Eggs', 100), sortKey: field('c0', 100) }),
        ],
      },
      error: undefined,
    })
    let resolvePatch!: (v: unknown) => void
    mockedApi.PATCH.mockImplementation(() => new Promise((res) => (resolvePatch = res)))
    const { result } = await renderLoadedHook()

    // Both items default to SONSTIGES — move item-2 to index 0 (before item-1), same section.
    await act(async () => {
      void result.current.moveItemTo('item-2', 'SONSTIGES', 0)
    })

    const moved = result.current.items.find((i) => i.id === 'item-2')!
    expect(moved.sortKey.timestamp).toBe(100) // not fabricated
    expect(moved.sortKey.modifiedBy).toBe(OTHER_USER)
    expect(moved.section.value).toBe('SONSTIGES') // unchanged — same section, no SECTION patch needed
    const newKey = moved.sortKey.value
    expect(newKey).not.toBe('c0')
    expect(mockedApi.PATCH).toHaveBeenCalledTimes(1)
    expect(mockedApi.PATCH).toHaveBeenCalledWith(
      '/lists/{listId}/items/{itemId}',
      expect.objectContaining({
        body: { field: 'SORT_KEY', value: newKey, modifiedBy: USER_ID },
      }),
    )

    resolvePatch({
      data: serverItem({ id: 'item-2', name: field('Eggs', 100), sortKey: field(newKey, 777, USER_ID) }),
      error: undefined,
    })
    await waitFor(() =>
      expect(result.current.items.find((i) => i.id === 'item-2')!.sortKey.timestamp).toBe(777),
    )
  })

  it('moveItemTo across sections sends two PATCHes (SORT_KEY then SECTION) and updates both optimistically', async () => {
    mockedApi.GET.mockResolvedValue({
      data: {
        id: LIST_ID,
        name: 'Groceries',
        items: [
          serverItem({ section: field('OBST_GEMUESE', 100) }),
          serverItem({
            id: 'item-2',
            name: field('Milch', 100),
            sortKey: field('c0', 100),
            section: field('MOLKEREI_EIER', 100),
          }),
        ],
      },
      error: undefined,
    })
    mockedApi.PATCH.mockImplementation((_path: string, opts: { body: { field: string; value: string } }) =>
      Promise.resolve({
        data: serverItem({
          id: 'item-1',
          name: field('Milk', 100),
          [opts.body.field === 'SORT_KEY' ? 'sortKey' : 'section']: field(
            opts.body.value,
            888,
            USER_ID,
          ),
        }),
        error: undefined,
      }),
    )
    const { result } = await renderLoadedHook()

    // item-1 starts in OBST_GEMUESE; move it into MOLKEREI_EIER at index 0.
    await act(async () => {
      void result.current.moveItemTo('item-1', 'MOLKEREI_EIER', 0)
    })

    expect(mockedApi.PATCH).toHaveBeenCalledTimes(2)
    const [sortCall, sectionCall] = mockedApi.PATCH.mock.calls
    expect(sortCall[1].body.field).toBe('SORT_KEY')
    expect(sectionCall[1].body).toEqual({
      field: 'SECTION',
      value: 'MOLKEREI_EIER',
      modifiedBy: USER_ID,
    })
  })

  it('setSection changes only the value optimistically then PATCHes SECTION', async () => {
    let resolvePatch!: (v: unknown) => void
    mockedApi.PATCH.mockImplementation(() => new Promise((res) => (resolvePatch = res)))
    const { result } = await renderLoadedHook()

    act(() => {
      void result.current.setSection('item-1', 'GETRAENKE')
    })

    expect(result.current.items[0].section).toEqual({
      value: 'GETRAENKE',
      timestamp: 100,
      modifiedBy: OTHER_USER,
    })
    expect(mockedApi.PATCH).toHaveBeenCalledWith(
      '/lists/{listId}/items/{itemId}',
      expect.objectContaining({
        body: { field: 'SECTION', value: 'GETRAENKE', modifiedBy: USER_ID },
      }),
    )

    resolvePatch({
      data: serverItem({ section: field('GETRAENKE', 999, USER_ID) }),
      error: undefined,
    })
    await waitFor(() => expect(result.current.items[0].section.timestamp).toBe(999))
  })

  it('setSection and moveItemTo do nothing without an authenticated user', async () => {
    mocks.user = null
    const { result } = await renderLoadedHook()
    await act(async () => {
      await result.current.setSection('item-1', 'GETRAENKE')
      await result.current.moveItemTo('item-1', 'SONSTIGES', 0)
    })
    expect(mockedApi.PATCH).not.toHaveBeenCalled()
  })

  it('updateItem reconciles the PATCH response into state', async () => {
    mockedApi.PATCH.mockResolvedValue({
      data: serverItem({ name: field('Bread', 500, USER_ID) }),
      error: undefined,
    })
    const { result } = await renderLoadedHook()
    await act(async () => {
      await result.current.updateItem('item-1', 'NAME', 'Bread')
    })
    expect(result.current.items[0].name).toEqual({ value: 'Bread', timestamp: 500, modifiedBy: USER_ID })
  })

  it('does nothing without an authenticated user', async () => {
    mocks.user = null
    const { result } = await renderLoadedHook()
    await act(async () => {
      await result.current.checkItem('item-1', true)
      await result.current.updateItem('item-1', 'NAME', 'x')
      await result.current.moveItemTo('item-1', 'SONSTIGES', 0)
    })
    expect(mockedApi.PATCH).not.toHaveBeenCalled()
  })
})

describe('useShoppingList — addItem', () => {
  it('appends the created item when it is not yet in state', async () => {
    mockedApi.GET.mockResolvedValue({ data: { id: LIST_ID, name: 'Groceries', items: [] }, error: undefined })
    const { result } = await renderLoadedHook()
    await act(async () => {
      await result.current.addItem('Milk')
    })
    expect(result.current.items).toHaveLength(1)
    expect(result.current.items[0].name.value).toBe('Milk')
  })

  it('merges the authoritative response per-field when SSE raced ahead', async () => {
    mockedApi.GET.mockResolvedValue({ data: { id: LIST_ID, name: 'Groceries', items: [] }, error: undefined })
    let resolvePost!: (v: unknown) => void
    mockedApi.POST.mockImplementation((path: string) => {
      if (String(path).includes('sse-token')) {
        return Promise.resolve({ data: { token: 'sse-tok' }, error: undefined })
      }
      return new Promise((res) => (resolvePost = res))
    })
    const { result } = await renderLoadedHook()
    const es = FakeEventSource.instances[0]

    await act(async () => {
      void result.current.addItem('Eggs')
    })

    // SSE races ahead: a newer CHECKED change materializes a partial item
    act(() => {
      es.emit(
        'item-change',
        JSON.stringify(changeEvent({ itemId: 'new-1', field: 'CHECKED', value: 'true', timestamp: 500 })),
      )
    })
    expect(result.current.items[0].name.value).toBe('') // partially materialized

    // POST response arrives with the full item (older timestamps)
    resolvePost({
      data: serverItem({
        id: 'new-1',
        name: field('Eggs', 400, USER_ID),
        quantity: field('1', 400, USER_ID),
        checked: field(false, 400, USER_ID),
        deleted: field(false, 400, USER_ID),
        sortKey: field('d0', 400, USER_ID),
      }),
      error: undefined,
    })

    await waitFor(() => expect(result.current.items[0].name.value).toBe('Eggs'))
    // Per-field merge: the newer SSE CHECKED change survives the response merge
    expect(result.current.items[0].checked).toEqual({ value: true, timestamp: 500, modifiedBy: OTHER_USER })
    expect(result.current.items).toHaveLength(1) // no duplicate
  })
})

describe('useShoppingList — SSE reconnect', () => {
  it('reconnects with a fresh token after an error, backing off and resetting on success', async () => {
    const { unmount } = await renderLoadedHook()
    const tokenCalls = () =>
      mockedApi.POST.mock.calls.filter(([p]) => String(p).includes('sse-token')).length
    expect(tokenCalls()).toBe(1)

    vi.useFakeTimers()

    // First error: closes the stream and schedules a reconnect in 1s
    const es1 = FakeEventSource.instances[0]
    act(() => es1.onerror?.())
    expect(es1.closed).toBe(true)
    expect(FakeEventSource.instances).toHaveLength(1)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await flush()
    expect(tokenCalls()).toBe(2) // fresh token fetched
    expect(FakeEventSource.instances).toHaveLength(2)

    // Second error: backoff doubles to 2s
    act(() => FakeEventSource.instances[1].onerror?.())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await flush()
    expect(FakeEventSource.instances).toHaveLength(2) // not yet
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await flush()
    expect(FakeEventSource.instances).toHaveLength(3)

    // Successful open resets the backoff to 1s
    act(() => FakeEventSource.instances[2].onopen?.())
    act(() => FakeEventSource.instances[2].onerror?.())
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await flush()
    expect(FakeEventSource.instances).toHaveLength(4)

    // Unmount cancels pending reconnects and closes the stream
    act(() => FakeEventSource.instances[3].onerror?.())
    unmount()
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000)
    })
    await flush()
    expect(FakeEventSource.instances).toHaveLength(4)
  })

  it('retries when the sse-token fetch itself fails', async () => {
    let tokenAttempts = 0
    mockedApi.POST.mockImplementation((path: string) => {
      if (String(path).includes('sse-token')) {
        tokenAttempts += 1
        if (tokenAttempts === 1) return Promise.reject(new Error('network'))
        return Promise.resolve({ data: { token: 'sse-tok-2' }, error: undefined })
      }
      return Promise.resolve({ data: serverItem(), error: undefined })
    })

    vi.useFakeTimers()
    const { result } = renderHook(() => useShoppingList(LIST_ID))
    await flush()
    expect(result.current.isLoading).toBe(false)
    expect(FakeEventSource.instances).toHaveLength(0)

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000)
    })
    await flush()
    expect(tokenAttempts).toBe(2)
    expect(FakeEventSource.instances).toHaveLength(1)
  })
})
