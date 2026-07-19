import { useCallback, useEffect, useRef, useState } from 'react'
import { apiClient } from '../../../api/client'
import { useAuth } from '../../auth/AuthContext'
import {
  applyChange,
  mergeItem,
  sortItems,
  type ItemChangeEvent,
  type ItemField,
  type ShoppingItem,
} from '../utils/lwwMerge'
import { between } from '../utils/fractionalIndex'

// SSE reconnect backoff bounds (exported for tests)
export const SSE_RECONNECT_INITIAL_DELAY_MS = 1_000
export const SSE_RECONNECT_MAX_DELAY_MS = 30_000

function toClientItem(raw: {
  id: string
  listId: string
  name: { value: string; timestamp: number; modifiedBy: string }
  quantity: { value: string; timestamp: number; modifiedBy: string }
  checked: { value: boolean; timestamp: number; modifiedBy: string }
  deleted: { value: boolean; timestamp: number; modifiedBy: string }
  sortKey: { value: string; timestamp: number; modifiedBy: string }
  section: { value: string; timestamp: number; modifiedBy: string }
}): ShoppingItem {
  return {
    id: raw.id,
    listId: raw.listId,
    name: raw.name,
    quantity: raw.quantity,
    checked: raw.checked,
    deleted: raw.deleted,
    sortKey: raw.sortKey,
    section: raw.section,
  }
}

export function useShoppingList(listId: string) {
  const { user } = useAuth()
  const [items, setItems] = useState<ShoppingItem[]>([])
  const [listName, setListName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const esRef = useRef<EventSource | null>(null)

  // Load initial state
  useEffect(() => {
    setIsLoading(true)
    apiClient
      .GET('/lists/{listId}', { params: { path: { listId } } })
      .then(({ data, error: apiError }) => {
        if (apiError || !data) {
          setError('Failed to load shopping list.')
          return
        }
        setListName(data.name)
        setItems((data.items ?? []).map(toClientItem))
      })
      .catch(() => setError('Failed to load shopping list.'))
      .finally(() => setIsLoading(false))
  }, [listId])

  // SSE subscription with automatic reconnect.
  // On any error the EventSource is closed, a fresh short-lived SSE token is
  // fetched, and we re-subscribe — with exponential backoff (1s → 30s cap).
  useEffect(() => {
    let cancelled = false
    let retryDelay = SSE_RECONNECT_INITIAL_DELAY_MS
    let retryTimer: ReturnType<typeof setTimeout> | null = null

    function scheduleReconnect() {
      if (cancelled || retryTimer !== null) return
      retryTimer = setTimeout(() => {
        retryTimer = null
        void connect()
      }, retryDelay)
      retryDelay = Math.min(retryDelay * 2, SSE_RECONNECT_MAX_DELAY_MS)
    }

    async function connect() {
      if (cancelled) return
      let token: string | undefined
      try {
        const { data } = await apiClient.POST('/lists/{listId}/sse-token', {
          params: { path: { listId } },
        })
        token = data?.token
      } catch {
        token = undefined
      }
      if (cancelled) return
      if (!token) {
        // Token fetch failed — retry with backoff
        scheduleReconnect()
        return
      }

      const es = new EventSource(`/api/lists/${listId}/events?token=${encodeURIComponent(token)}`)
      esRef.current = es

      es.onopen = () => {
        // Connection established — reset backoff
        retryDelay = SSE_RECONNECT_INITIAL_DELAY_MS
      }

      es.addEventListener('item-change', (evt: MessageEvent) => {
        try {
          const change = JSON.parse(evt.data as string) as ItemChangeEvent
          setItems((prev) => applyChange(prev, change))
        } catch {
          // Ignore malformed events
        }
      })

      es.onerror = () => {
        es.close()
        if (esRef.current === es) esRef.current = null
        scheduleReconnect()
      }
    }

    void connect()

    return () => {
      cancelled = true
      if (retryTimer !== null) clearTimeout(retryTimer)
      esRef.current?.close()
      esRef.current = null
    }
  }, [listId])

  /**
   * Reconcile an authoritative server item (POST/PATCH response body) into
   * local state, merging per-field so a concurrently-arrived newer SSE event
   * is never overwritten by an older response.
   */
  const reconcileServerItem = useCallback((raw: Parameters<typeof toClientItem>[0]) => {
    const incoming = toClientItem(raw)
    setItems((prev) => {
      if (prev.some((i) => i.id === incoming.id)) {
        return prev.map((i) => (i.id === incoming.id ? mergeItem(i, incoming) : i))
      }
      return [...prev, incoming]
    })
  }, [])

  const addItem = useCallback(
    async (name: string, quantity?: string) => {
      const { data, error: apiError } = await apiClient.POST('/lists/{listId}/items', {
        params: { path: { listId } },
        body: { name, quantity: quantity || '1' },
      })
      if (!apiError && data) {
        // If SSE events raced ahead and already (partially) materialized this
        // item, merge the authoritative response per-field instead of skipping.
        reconcileServerItem(data)
      }
    },
    [listId, reconcileServerItem],
  )

  const updateItem = useCallback(
    async (itemId: string, field: ItemField, value: string) => {
      if (!user) return
      const { data, error: apiError } = await apiClient.PATCH('/lists/{listId}/items/{itemId}', {
        params: { path: { listId, itemId } },
        body: { field, value, modifiedBy: user.id },
      })
      if (!apiError && data) {
        reconcileServerItem(data)
      }
    },
    [listId, user, reconcileServerItem],
  )

  const checkItem = useCallback(
    async (itemId: string, checked: boolean) => {
      if (!user) return
      // Optimistic update: change only the value. Timestamps are always
      // server-assigned — never fabricate one from the client clock, or a
      // fast local clock would beat genuine later server events in LWW merge.
      setItems((prev) =>
        prev.map((i) =>
          i.id === itemId ? { ...i, checked: { ...i.checked, value: checked } } : i,
        ),
      )
      const { data, error: apiError } = await apiClient.PATCH('/lists/{listId}/items/{itemId}', {
        params: { path: { listId, itemId } },
        body: { field: 'CHECKED', value: String(checked), modifiedBy: user.id },
      })
      if (!apiError && data) {
        reconcileServerItem(data)
      }
    },
    [listId, user, reconcileServerItem],
  )

  const deleteItem = useCallback(
    async (itemId: string) => {
      // Optimistic tombstone: change only the value, keep server timestamp.
      // The SSE echo delivers the authoritative DELETED change.
      setItems((prev) =>
        prev.map((i) =>
          i.id === itemId ? { ...i, deleted: { ...i.deleted, value: true } } : i,
        ),
      )
      await apiClient.DELETE('/lists/{listId}/items/{itemId}', {
        params: { path: { listId, itemId } },
      })
    },
    [listId],
  )

  const moveItem = useCallback(
    async (itemId: string, afterItemId: string | null) => {
      if (!user) return
      const sorted = sortItems(items)
      const afterIndex = afterItemId ? sorted.findIndex((i) => i.id === afterItemId) : -1

      const prevItem = afterIndex >= 0 ? sorted[afterIndex] : null
      const nextItem = afterIndex >= 0 && afterIndex + 1 < sorted.length ? sorted[afterIndex + 1] : null

      const newKey = between(
        prevItem?.sortKey.value ?? null,
        nextItem?.sortKey.value ?? null,
      )

      // Optimistic update: value only, no client-fabricated timestamp.
      setItems((prev) =>
        prev.map((i) =>
          i.id === itemId ? { ...i, sortKey: { ...i.sortKey, value: newKey } } : i,
        ),
      )

      const { data, error: apiError } = await apiClient.PATCH('/lists/{listId}/items/{itemId}', {
        params: { path: { listId, itemId } },
        body: { field: 'SORT_KEY', value: newKey, modifiedBy: user.id },
      })
      if (!apiError && data) {
        reconcileServerItem(data)
      }
    },
    [listId, user, items, reconcileServerItem],
  )

  return {
    items: sortItems(items),
    listName,
    error,
    isLoading,
    addItem,
    updateItem,
    checkItem,
    deleteItem,
    moveItem,
  }
}
