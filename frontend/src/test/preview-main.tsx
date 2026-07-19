/**
 * Visual preview harness — dev-only, never shipped.
 *
 * Served from /preview.html by the Vite dev server. Stubs fetch + EventSource
 * with realistic in-memory data so every screen state can be rendered (and
 * screenshotted) without a backend.
 *
 *   /preview.html?screen=login | lists | lists-empty | lists-loading
 *                | list | list-empty | list-loading | list-error | callback-error
 *   &sheet=create | share   — auto-opens the corresponding dialog
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import '../index.css'

const params = new URLSearchParams(window.location.search)
const screen = params.get('screen') ?? 'list'
const sheet = params.get('sheet')

const USER = { id: 'u1', email: 'alice@example.com', displayName: 'Alice' }
const MEMBERS = [
  USER,
  { id: 'u2', email: 'ben@example.com', displayName: 'Ben' },
  { id: 'u3', email: 'cara@example.com', displayName: 'Cara' },
]

function lww<T>(value: T) {
  return { value, timestamp: 1700000000000, modifiedBy: 'u1' }
}

function makeItem(
  id: string,
  name: string,
  sortKey: string,
  opts: { qty?: string; checked?: boolean; section?: string } = {},
) {
  return {
    id,
    listId: 'l1',
    name: lww(name),
    quantity: lww(opts.qty ?? '1'),
    checked: lww(opts.checked ?? false),
    deleted: lww(false),
    sortKey: lww(sortKey),
    section: lww(opts.section ?? 'SONSTIGES'),
  }
}

const ITEMS =
  screen === 'list-empty'
    ? []
    : [
        makeItem('i1', 'Äpfel', 'a0', { qty: '6', section: 'OBST_GEMUESE' }),
        makeItem('i2', 'Kirschtomaten', 'b0', { section: 'OBST_GEMUESE' }),
        makeItem('i3', 'Basilikum', 'c0', { qty: '2 Töpfe', section: 'OBST_GEMUESE' }),
        makeItem('i4', 'Vollkornbrot', 'd0', { section: 'BROT_BACKWAREN' }),
        makeItem('i5', 'Brötchen', 'e0', { qty: '6', section: 'BROT_BACKWAREN' }),
        makeItem('i6', 'Milch', 'f0', { qty: '2 l', section: 'MOLKEREI_EIER' }),
        makeItem('i7', 'Eier', 'g0', { qty: '10', section: 'MOLKEREI_EIER' }),
        makeItem('i8', 'Hähnchenbrust', 'h0', { section: 'FLEISCH_FISCH' }),
        makeItem('i9', 'Tiefkühlerbsen', 'i0', { section: 'TIEFKUEHL' }),
        makeItem('i10', 'Spaghetti', 'j0', { section: 'VORRAT' }),
        makeItem('i11', 'Olivenöl', 'k0', { section: 'GEWUERZE_SOSSEN', checked: true }),
        makeItem('i12', 'Butter', 'l0', { section: 'MOLKEREI_EIER', checked: true }),
      ]

const LISTS =
  screen === 'lists-empty'
    ? []
    : [
        { id: 'l1', name: 'Groceries', ownerId: 'u1', members: MEMBERS, createdAt: '2026-07-01T10:00:00Z' },
        { id: 'l2', name: 'Weekend BBQ', ownerId: 'u2', members: MEMBERS.slice(0, 2), createdAt: '2026-07-10T10:00:00Z' },
        { id: 'l3', name: 'Hardware store', ownerId: 'u1', members: [USER], createdAt: '2026-07-15T10:00:00Z' },
      ]

const NEVER = new Promise<Response>(() => {})

function json(body: unknown): Promise<Response> {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }),
  )
}

window.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  const path = new URL(url, window.location.origin).pathname
  const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()

  if (path === '/api/users/me') return json(USER)
  if (path === '/api/lists' && method === 'GET') {
    if (screen === 'lists-loading') return NEVER
    return json(LISTS)
  }
  if (/^\/api\/lists\/[^/]+\/sse-token$/.test(path)) return NEVER
  if (/^\/api\/lists\/[^/]+$/.test(path) && method === 'GET') {
    if (screen === 'list-loading') return NEVER
    if (screen === 'list-error') return Promise.resolve(new Response('nope', { status: 403 }))
    return json({ id: 'l1', name: 'Groceries', ownerId: 'u1', members: MEMBERS, items: ITEMS })
  }
  // Mutations: echo something plausible so optimistic flows settle quietly
  return json(ITEMS[0] ?? {})
}

class FakeEventSource {
  onopen: (() => void) | null = null
  onerror: (() => void) | null = null
  addEventListener() {}
  close() {}
}
;(window as unknown as { EventSource: unknown }).EventSource = FakeEventSource

if (screen !== 'login' && screen !== 'callback-error') {
  localStorage.setItem('auth_token', 'preview-token')
} else {
  localStorage.removeItem('auth_token')
}

const route =
  screen === 'login'
    ? '/login'
    : screen === 'callback-error'
      ? '/auth/callback'
      : screen.startsWith('lists')
        ? '/lists'
        : '/lists/l1'

async function mount() {
  // Imported dynamically so the fetch/EventSource stubs above are installed
  // before api/client captures globalThis.fetch.
  const { AuthProvider } = await import('../features/auth/AuthContext')
  const { LoginPage } = await import('../features/auth/LoginPage')
  const { AuthCallback } = await import('../features/auth/AuthCallback')
  const { ProtectedRoute } = await import('../features/auth/ProtectedRoute')
  const { ListsPage } = await import('../features/shopping-list/components/ListsPage')
  const { ShoppingListPage } = await import('../features/shopping-list/components/ShoppingListPage')

  const router = createMemoryRouter(
    [
      { path: '/login', element: <AuthProvider><LoginPage /></AuthProvider> },
      { path: '/auth/callback', element: <AuthProvider><AuthCallback /></AuthProvider> },
      {
        path: '/lists',
        element: <AuthProvider><ProtectedRoute><ListsPage /></ProtectedRoute></AuthProvider>,
      },
      {
        path: '/lists/:listId',
        element: <AuthProvider><ProtectedRoute><ShoppingListPage /></ProtectedRoute></AuthProvider>,
      },
    ],
    { initialEntries: [route] },
  )

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <RouterProvider router={router} />
    </StrictMode>,
  )

  if (sheet) {
    setTimeout(() => {
      const label = sheet === 'create' ? /new list/i : /^share$/i
      const btn = Array.from(document.querySelectorAll('button')).find((b) =>
        label.test(b.textContent ?? ''),
      )
      btn?.click()
    }, 400)
  }
}

void mount()
