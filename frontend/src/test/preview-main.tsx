/**
 * Visual preview harness — dev-only, never shipped.
 *
 * Served from /preview.html by the Vite dev server. Stubs fetch + EventSource
 * with realistic in-memory data so every screen state can be rendered (and
 * screenshotted) without a backend.
 *
 *   /preview.html?screen=login | welcome | welcome-name | lists | lists-empty
 *                | lists-loading | list | list-empty | list-loading | list-error
 *                | callback-error
 *   &sheet=create | group | picnic-credentials | picnic-export
 *                — auto-opens the corresponding dialog
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import '../index.css'

const params = new URLSearchParams(window.location.search)
const screen = params.get('screen') ?? 'list'
const sheet = params.get('sheet')

const GROUP = { id: 'g1', name: 'The Sandbergs' }
// The welcome screens are exactly the group-less state, so the profile must
// come back without a group there — that is what RequireGroup keys off.
const HAS_GROUP = screen !== 'welcome' && screen !== 'welcome-name'
const USER = {
  id: 'u1',
  email: 'alice@example.com',
  displayName: 'Alice',
  group: HAS_GROUP ? GROUP : null,
}
const MEMBERS = [
  USER,
  { id: 'u2', email: 'ben@example.com', displayName: 'Ben', group: GROUP },
  { id: 'u3', email: 'cara@example.com', displayName: 'Cara', group: GROUP },
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
        { id: 'l1', name: 'Groceries', ownerId: 'u1', groupId: GROUP.id, createdAt: '2026-07-01T10:00:00Z' },
        { id: 'l2', name: 'Weekend BBQ', ownerId: 'u2', groupId: GROUP.id, createdAt: '2026-07-10T10:00:00Z' },
        { id: 'l3', name: 'Hardware store', ownerId: 'u1', groupId: GROUP.id, createdAt: '2026-07-15T10:00:00Z' },
      ]

/**
 * Picnic candidates per item id — the export sheet asks for one item at a time. Items with
 * no entry here answer with an empty list, which is the "this item will be skipped" path.
 */
const PICNIC_SUGGESTIONS: Record<string, { itemName: string; suggestions: unknown[] }> = {
  i1: {
    itemName: 'Äpfel',
    suggestions: [
      {
        id: 'a1',
        name: 'Elstar Äpfel, 1kg',
        imageUrl: 'https://picnic.example/img/apples-elstar.jpg',
        priceCents: 249,
        unit: '1kg',
      },
      {
        id: 'a2',
        name: 'Bio Äpfel Jonagold, 1kg',
        imageUrl: 'https://picnic.example/img/apples-jonagold.jpg',
        priceCents: 329,
        unit: '1kg',
      },
      // Deliberately missing imageUrl/priceCents/unit — exercises the
      // "no thumbnail, no price" rendering path.
      { id: 'a3', name: 'Äpfel lose' },
    ],
  },
  i2: {
    itemName: 'Kirschtomaten',
    suggestions: [
      {
        id: 't1',
        name: 'Kirschtomaten, 250g',
        imageUrl: 'https://picnic.example/img/kirschtomaten.jpg',
        priceCents: 179,
        unit: '250g',
      },
      {
        id: 't2',
        name: 'Bio Kirschtomaten, 250g',
        imageUrl: 'https://picnic.example/img/bio-kirschtomaten.jpg',
        priceCents: 229,
        unit: '250g',
      },
    ],
  },
  i6: { itemName: 'Milch', suggestions: [{ id: 'm1', name: 'Frische Vollmilch' }] },
  // No matches at all — exercises the "this item will be skipped" path.
  i4: { itemName: 'Vollkornbrot', suggestions: [] },
}

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
  if (path === '/api/groups/me') {
    return json({ ...GROUP, createdAt: '2026-06-01T10:00:00Z', members: MEMBERS })
  }
  if (path === '/api/invites' && method === 'POST') {
    const type = JSON.parse((init?.body as string) ?? '{}').type ?? 'JOIN_GROUP'
    return Promise.resolve(
      new Response(
        JSON.stringify({ code: 'K7M2QRXP', type, expiresAt: '2026-07-27T10:00:00Z' }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    )
  }
  if (path === '/api/invites/redeem' && method === 'POST') {
    // welcome-name previews the second step: the backend answers the bare code
    // with GROUP_NAME_REQUIRED (without consuming it) so the UI reveals the
    // "name your household" field.
    const body = JSON.parse((init?.body as string) ?? '{}')
    if (screen === 'welcome-name' && !body.groupName) {
      return Promise.resolve(
        new Response(JSON.stringify({ code: 'GROUP_NAME_REQUIRED', message: 'Group name required' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      )
    }
    return json({ ...USER, group: GROUP })
  }
  if (path === '/api/lists' && method === 'GET') {
    if (screen === 'lists-loading') return NEVER
    return json(LISTS)
  }
  if (/^\/api\/lists\/[^/]+\/sse-token$/.test(path)) return NEVER
  if (/^\/api\/lists\/[^/]+$/.test(path) && method === 'GET') {
    if (screen === 'list-loading') return NEVER
    if (screen === 'list-error') return Promise.resolve(new Response('nope', { status: 403 }))
    return json({ id: 'l1', name: 'Groceries', ownerId: 'u1', groupId: GROUP.id, items: ITEMS })
  }
  if (path === '/api/users/me/picnic-credentials' && method === 'GET') {
    // Unlinked by default — more useful to preview/screenshot than the linked state.
    return json({ linked: false })
  }
  if (path === '/api/picnic/search-terms' && method === 'GET') {
    return json({ terms: ['bio äpfel', 'äpfel lose', 'apfelsaft', 'apfelmus'] })
  }
  const suggestionsMatch = /^\/api\/lists\/[^/]+\/picnic\/suggestions\/([^/?]+)/.exec(path)
  if (suggestionsMatch && method === 'POST') {
    // The `picnic-credentials` sheet preview drives the export sheet into its
    // credentials-missing error branch so the auto-click chain below can reach
    // PicnicCredentialsSheet — the only way that sheet is shown on this page.
    if (sheet === 'picnic-credentials') {
      return Promise.resolve(
        new Response(
          JSON.stringify({ code: 'PICNIC_CREDENTIALS_MISSING', message: 'Link a Picnic account first' }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      )
    }
    const itemId = suggestionsMatch[1]
    // `path` is the pathname only, so the override has to come off the full url.
    const overrideTerm = new URL(url, window.location.origin).searchParams.get('searchTerm')
    const entry = PICNIC_SUGGESTIONS[itemId] ?? {
      itemName: itemId,
      suggestions: [],
    }
    return json({
      itemId,
      itemName: entry.itemName,
      searchTerm: overrideTerm ?? entry.itemName,
      suggestions: entry.suggestions,
    })
  }
  if (/^\/api\/lists\/[^/]+\/picnic\/export$/.test(path) && method === 'POST') {
    // Matches the default selections above: a1/t1/m1 picked, Vollkornbrot has
    // no candidate so it's skipped.
    return json({ added: 3, skipped: 1, failures: [] })
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
      : screen.startsWith('welcome')
        ? '/welcome'
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
  const { RequireGroup } = await import('../features/auth/RequireGroup')
  const { OnboardingPage } = await import('../features/onboarding/OnboardingPage')
  const { ListsPage } = await import('../features/shopping-list/components/ListsPage')
  const { ShoppingListPage } = await import('../features/shopping-list/components/ShoppingListPage')

  const router = createMemoryRouter(
    [
      { path: '/login', element: <AuthProvider><LoginPage /></AuthProvider> },
      { path: '/auth/callback', element: <AuthProvider><AuthCallback /></AuthProvider> },
      {
        path: '/welcome',
        element: <AuthProvider><ProtectedRoute><OnboardingPage /></ProtectedRoute></AuthProvider>,
      },
      {
        path: '/lists',
        element: (
          <AuthProvider><ProtectedRoute><RequireGroup><ListsPage /></RequireGroup></ProtectedRoute></AuthProvider>
        ),
      },
      {
        path: '/lists/:listId',
        element: (
          <AuthProvider><ProtectedRoute><RequireGroup><ShoppingListPage /></RequireGroup></ProtectedRoute></AuthProvider>
        ),
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
      const label =
        sheet === 'create'
          ? /new list/i
          : sheet === 'group'
            ? /your group/i
            : /export to picnic/i // picnic-credentials | picnic-export share one trigger
      // The group/export triggers are icon buttons carrying only an aria-label.
      const btn = Array.from(document.querySelectorAll('button')).find(
        (b) => label.test(b.textContent ?? '') || label.test(b.getAttribute('aria-label') ?? ''),
      )
      btn?.click()

      if (sheet === 'picnic-credentials') {
        // The suggestions mock above answers with PICNIC_CREDENTIALS_MISSING
        // for this sheet value, so the export sheet renders a "Link Picnic
        // account" button — click through to actually land on the
        // credentials sheet, which has no direct trigger of its own.
        setTimeout(() => {
          const linkBtn = Array.from(document.querySelectorAll('button')).find((b) =>
            /link picnic account/i.test(b.textContent ?? ''),
          )
          linkBtn?.click()
        }, 400)
      }
    }, 400)
  }

  // welcome-name shows the second onboarding step, which is only reachable by
  // submitting a code and getting GROUP_NAME_REQUIRED back. Drive that here so
  // the screen is directly linkable.
  if (screen === 'welcome-name') {
    setTimeout(() => {
      const input = document.querySelector<HTMLInputElement>('#invite-code')
      if (!input) return
      const setValue = Object.getOwnPropertyDescriptor(
        window.HTMLInputElement.prototype,
        'value',
      )?.set
      setValue?.call(input, 'K7M2QRXP')
      input.dispatchEvent(new Event('input', { bubbles: true }))
      setTimeout(() => {
        document.querySelector<HTMLFormElement>('form')?.requestSubmit()
      }, 50)
    }, 400)
  }
}

void mount()
