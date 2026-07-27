import { useCallback, useEffect, useRef, useState } from 'react'
import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

type ItemSuggestions = components['schemas']['ItemSuggestions']
type ExportResult = components['schemas']['ExportResult']

/** The minimum an item needs to be exportable — the page already has both. */
export interface ExportableItem {
  id: string
  name: string
}

interface PicnicExportSheetProps {
  listId: string
  items: ExportableItem[]
  onClose: () => void
  onNeedsCredentials: () => void
}

type Step = 'stepping' | 'submitting' | 'result' | 'error'

/** Long enough that typing a word costs one autocomplete call, short enough to feel live. */
const AUTOCOMPLETE_DEBOUNCE_MS = 250

const CREDENTIALS_MISSING_MESSAGE = 'Link a Picnic account to export this list.'
const SECOND_FACTOR_MESSAGE = 'Finish linking your Picnic account — it still needs the SMS code.'
const SESSION_EXPIRED_MESSAGE = 'Your Picnic session has expired. Link your account again to keep exporting.'
const GENERIC_ERROR = "Picnic isn't available right now — try again later."

/**
 * Three different failures all mean "the user must go to the credentials sheet",
 * and none of them is fixed by retrying: nothing is linked, linking was never
 * finished, or the stored session died. Picnic's 2FA cannot be re-cleared without
 * the user, so an expired session is a dead end here, not a transient error.
 */
const RELINK_MESSAGES: Record<string, string> = {
  PICNIC_CREDENTIALS_MISSING: CREDENTIALS_MISSING_MESSAGE,
  PICNIC_SECOND_FACTOR_REQUIRED: SECOND_FACTOR_MESSAGE,
  PICNIC_SESSION_EXPIRED: SESSION_EXPIRED_MESSAGE,
}

function relinkMessageFor(code: string | undefined): string | null {
  return code === undefined ? null : (RELINK_MESSAGES[code] ?? null)
}

function formatPrice(priceCents?: number | null): string | null {
  if (priceCents === undefined || priceCents === null) return null
  return (priceCents / 100).toFixed(2) + ' €'
}

/**
 * Bottom sheet that exports a list's active items to a Picnic cart, one item at a
 * time. Each Picnic search costs ~2-3 s of render on their side, so fetching a
 * whole list up front made the wait grow with list size and paid for items the
 * user was about to skip. Stepping keeps time-to-first-choice constant, and the
 * next item is prefetched while the user decides — which hides almost all of that
 * latency, since deciding takes about as long as the fetch.
 *
 * The cart write still happens once at the end: adding as you go would leave a
 * half-filled real cart behind whenever someone abandons midway.
 */
export function PicnicExportSheet({
  listId,
  items,
  onClose,
  onNeedsCredentials,
}: PicnicExportSheetProps) {
  const [step, setStep] = useState<Step>('stepping')
  const [index, setIndex] = useState(0)
  const [suggestionsByItem, setSuggestionsByItem] = useState<Record<string, ItemSuggestions>>({})
  const [errorByItem, setErrorByItem] = useState<Record<string, string>>({})
  const [selections, setSelections] = useState<Record<string, string | undefined>>({})
  const [relinkMessage, setRelinkMessage] = useState<string | null>(null)
  const [result, setResult] = useState<ExportResult | null>(null)

  // The term each item is *searched* with, and the text sitting in its field. They differ
  // while the user is typing; committing a search makes them equal again. Both are keyed by
  // item so stepping back to an earlier item shows what was done to it.
  const [searchTermByItem, setSearchTermByItem] = useState<Record<string, string>>({})
  const [draftByItem, setDraftByItem] = useState<Record<string, string>>({})
  const [termOptions, setTermOptions] = useState<string[]>([])
  const [termOptionsOpen, setTermOptionsOpen] = useState(false)

  // Stops a prefetch and a navigation racing to fetch the same item twice — but keyed by the
  // term as well, so re-searching an item with a new term is never mistaken for a duplicate.
  const inFlight = useRef<Map<string, string>>(new Map())
  // The most recent term requested per item. A response for anything else is stale and gets
  // dropped: a slow search for "Milch" must not overwrite a fast one for "bio vollmilch".
  const latestTerm = useRef<Map<string, string>>(new Map())
  const latestAutocompleteQuery = useRef('')
  const cancelled = useRef(false)
  useEffect(() => {
    cancelled.current = false
    return () => {
      cancelled.current = true
    }
  }, [])

  const fetchItem = useCallback(
    async (item: ExportableItem, term: string) => {
      if (inFlight.current.get(item.id) === term) return
      inFlight.current.set(item.id, term)
      latestTerm.current.set(item.id, term)

      const { data, error: apiError } = await apiClient.POST(
        '/lists/{listId}/picnic/suggestions/{itemId}',
        {
          params: {
            path: { listId, itemId: item.id },
            // Omitted when it is just the item's name — that is what the server defaults to.
            query: term === item.name ? {} : { searchTerm: term },
          },
        },
      )

      if (inFlight.current.get(item.id) === term) inFlight.current.delete(item.id)
      if (cancelled.current || latestTerm.current.get(item.id) !== term) return

      if (apiError || !data) {
        const relink = relinkMessageFor(apiError?.code)
        if (relink) {
          // Nothing about this is per-item — the whole sheet is dead until the user
          // re-links, so stop rather than letting them step on into more failures.
          setRelinkMessage(relink)
          setStep('error')
          return
        }
        setErrorByItem((prev) => ({ ...prev, [item.id]: GENERIC_ERROR }))
        return
      }

      setSuggestionsByItem((prev) => ({ ...prev, [item.id]: data }))
      setErrorByItem((prev) => {
        const next = { ...prev }
        delete next[item.id]
        return next
      })
      // Default to the top match; the user overrides or skips.
      setSelections((prev) =>
        item.id in prev ? prev : { ...prev, [item.id]: data.suggestions[0]?.id },
      )
    },
    [listId],
  )

  const current = items[index]
  const currentData = current ? suggestionsByItem[current.id] : undefined
  const currentError = current ? errorByItem[current.id] : undefined
  // An item is searched for its own name until the user says otherwise.
  const currentTerm = current ? (searchTermByItem[current.id] ?? current.name) : ''
  const currentDraft = current ? (draftByItem[current.id] ?? currentTerm) : ''

  useEffect(() => {
    if (step !== 'stepping' || !current) return
    if (!suggestionsByItem[current.id] && !errorByItem[current.id]) {
      void fetchItem(current, currentTerm)
    }
  }, [step, current, currentTerm, suggestionsByItem, errorByItem, fetchItem])

  // Prefetch the next item as soon as this one is on screen, so the user's
  // decision time doubles as its load time.
  useEffect(() => {
    if (step !== 'stepping' || !currentData) return
    const next = items[index + 1]
    if (next && !suggestionsByItem[next.id] && !errorByItem[next.id]) {
      void fetchItem(next, searchTermByItem[next.id] ?? next.name)
    }
  }, [step, currentData, index, items, suggestionsByItem, errorByItem, searchTermByItem, fetchItem])

  // Autocomplete only while the field is focused and showing something not yet searched —
  // otherwise every step through the list would fire a pointless call.
  useEffect(() => {
    if (!termOptionsOpen) return
    const query = currentDraft.trim()
    if (!query || query === currentTerm) {
      setTermOptions([])
      return
    }
    const timer = setTimeout(async () => {
      latestAutocompleteQuery.current = query
      const { data } = await apiClient.GET('/picnic/search-terms', {
        params: { query: { term: query } },
      })
      if (cancelled.current || latestAutocompleteQuery.current !== query) return
      // Failures are swallowed on purpose: these are hints over a field the user can type
      // into, so losing them should cost nothing visible.
      setTermOptions(data?.terms ?? [])
    }, AUTOCOMPLETE_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [termOptionsOpen, currentDraft, currentTerm])

  /**
   * Re-runs the search for one item under a new term. The cached results and the previous
   * pick are dropped rather than kept: the old selection almost never survives a new search,
   * and leaving it would silently export a product the user can no longer see.
   */
  function commitSearch(item: ExportableItem, rawTerm: string) {
    const term = rawTerm.trim()
    setTermOptions([])
    setTermOptionsOpen(false)
    if (!term || term === (searchTermByItem[item.id] ?? item.name)) return

    setDraftByItem((prev) => ({ ...prev, [item.id]: term }))
    setSearchTermByItem((prev) => ({ ...prev, [item.id]: term }))
    setSuggestionsByItem((prev) => {
      const next = { ...prev }
      delete next[item.id]
      return next
    })
    setErrorByItem((prev) => {
      const next = { ...prev }
      delete next[item.id]
      return next
    })
    setSelections((prev) => {
      const next = { ...prev }
      delete next[item.id]
      return next
    })
  }

  function goTo(nextIndex: number) {
    setTermOptions([])
    setTermOptionsOpen(false)
    setIndex(nextIndex)
  }

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function handleConfirm() {
    setStep('submitting')
    const { data, error: apiError } = await apiClient.POST('/lists/{listId}/picnic/export', {
      params: { path: { listId } },
      body: {
        selections: items.map((item) => ({
          itemId: item.id,
          articleId: selections[item.id],
        })),
      },
    })
    if (apiError || !data) {
      setRelinkMessage(relinkMessageFor(apiError?.code))
      setStep('error')
      return
    }
    setResult(data)
    setStep('result')
  }

  function itemNameFor(itemId: string): string {
    return items.find((item) => item.id === itemId)?.name ?? itemId
  }

  const isLast = index === items.length - 1

  return (
    <div
      className="sheet-backdrop fixed inset-0 bg-ink/40 flex items-end sm:items-center justify-center z-overlay px-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:pb-0"
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose()
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="picnic-export-sheet-title"
        className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet max-h-[80vh] overflow-y-auto"
      >
        <div className="flex items-start justify-between gap-3">
          <h2 id="picnic-export-sheet-title" className="text-title font-semibold text-ink mb-1">
            Export to Picnic
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="pressable -mt-1 -mr-1 min-h-touch min-w-touch rounded-full flex items-center justify-center text-ink-soft hover:bg-ground"
          >
            <svg className="w-5 h-5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <path
                d="M6 6l8 8M14 6l-8 8"
                stroke="currentColor"
                strokeWidth="1.75"
                strokeLinecap="round"
              />
            </svg>
          </button>
        </div>
        <span className="inline-block mb-3 text-label font-semibold text-honey-deep bg-marigold-tint rounded-full px-2 py-0.5">
          Beta — uses an unofficial Picnic API
        </span>

        {step === 'error' &&
          (relinkMessage ? (
            <div className="mt-4">
              <p className="text-body text-ink-soft mb-4">{relinkMessage}</p>
              <button
                type="button"
                onClick={onNeedsCredentials}
                className="pressable w-full min-h-touch px-4 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep"
              >
                Link Picnic account
              </button>
            </div>
          ) : (
            <div className="bg-danger-tint border border-danger/25 rounded-xl p-4 mt-4 text-danger text-body">
              {GENERIC_ERROR}
            </div>
          ))}

        {step !== 'error' && items.length === 0 && (
          <p className="text-body text-ink-soft mt-4">
            Nothing to export — every item on this list is already checked off.
          </p>
        )}

        {(step === 'stepping' || step === 'submitting') && current && (
          <>
            <p className="text-label text-ink-mute mt-4 mb-1">
              Item {index + 1} of {items.length}
            </p>
            <h3 className="text-body font-semibold text-ink mb-2">{current.name}</h3>

            <form
              className="relative mb-3"
              onSubmit={(e) => {
                e.preventDefault()
                commitSearch(current, currentDraft)
              }}
            >
              <label
                htmlFor={`picnic-search-${current.id}`}
                className="block text-label text-ink-mute mb-1"
              >
                Search Picnic for
              </label>
              <div className="flex gap-2">
                <input
                  id={`picnic-search-${current.id}`}
                  type="text"
                  value={currentDraft}
                  autoComplete="off"
                  maxLength={100}
                  onChange={(e) =>
                    setDraftByItem((prev) => ({ ...prev, [current.id]: e.target.value }))
                  }
                  onFocus={() => setTermOptionsOpen(true)}
                  // Deferred so a click on a suggestion below still registers.
                  onBlur={() => setTimeout(() => setTermOptionsOpen(false), 0)}
                  className="flex-1 min-w-0 min-h-touch px-3 py-2 border border-line rounded-xl text-body text-ink bg-panel focus:outline-none focus:ring-2 focus:ring-marigold-deep"
                />
                <button
                  type="submit"
                  disabled={
                    step === 'submitting' ||
                    !currentDraft.trim() ||
                    currentDraft.trim() === currentTerm
                  }
                  className="pressable min-h-touch px-4 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground disabled:opacity-50"
                >
                  Search
                </button>
              </div>

              {termOptionsOpen && termOptions.length > 0 && (
                <ul
                  aria-label="Suggested search terms"
                  className="absolute z-sheet left-0 right-0 mt-1 bg-panel border border-line rounded-xl shadow-lg overflow-hidden"
                >
                  {termOptions.map((term) => (
                    <li key={term}>
                      <button
                        type="button"
                        // Beats the input's blur, which would otherwise close this first.
                        onMouseDown={(e) => e.preventDefault()}
                        onClick={() => commitSearch(current, term)}
                        className="w-full text-left px-3 py-2 text-body text-ink hover:bg-ground"
                      >
                        {term}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </form>

            {!currentData && !currentError && (
              <div role="status" aria-label="Loading suggestions" className="space-y-3">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="space-y-2">
                    <div className="h-4 w-2/5 rounded bg-line animate-pulse" />
                    <div className="h-3 w-3/5 rounded bg-line/70 animate-pulse" />
                  </div>
                ))}
              </div>
            )}

            {currentError && (
              <div className="mt-1">
                <p
                  role="alert"
                  className="text-body text-danger bg-danger-tint border border-danger/25 rounded-xl px-3 py-2 mb-3"
                >
                  {currentError}
                </p>
                <button
                  type="button"
                  onClick={() =>
                    setErrorByItem((prev) => {
                      const next = { ...prev }
                      delete next[current.id]
                      return next
                    })
                  }
                  className="pressable w-full min-h-touch px-4 py-2.5 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground"
                >
                  Try this item again
                </button>
              </div>
            )}

            {currentData && currentData.suggestions.length === 0 && (
              <p className="text-label text-ink-mute">
                No matches found — this item will be skipped.
              </p>
            )}

            {currentData && currentData.suggestions.length > 0 && (
              <div
                role="radiogroup"
                aria-label={`Choose a Picnic product for ${current.name}`}
                className="space-y-1"
              >
                {currentData.suggestions.map((suggestion) => {
                  const price = formatPrice(suggestion.priceCents)
                  return (
                    <label
                      key={suggestion.id}
                      className="flex items-center gap-3 px-2 py-2 rounded-xl hover:bg-ground cursor-pointer"
                    >
                      <input
                        type="radio"
                        name={`item-${current.id}`}
                        checked={selections[current.id] === suggestion.id}
                        onChange={() =>
                          setSelections((prev) => ({ ...prev, [current.id]: suggestion.id }))
                        }
                        className="accent-marigold-deep h-4 w-4 flex-shrink-0"
                      />
                      {suggestion.imageUrl ? (
                        <img
                          src={suggestion.imageUrl}
                          alt=""
                          loading="lazy"
                          className="w-8 h-8 rounded object-cover flex-shrink-0 bg-line"
                          onError={(e) => {
                            e.currentTarget.style.display = 'none'
                          }}
                        />
                      ) : (
                        <span aria-hidden="true" className="w-8 h-8 rounded bg-line flex-shrink-0" />
                      )}
                      <span className="min-w-0 flex-1">
                        <span className="block text-body text-ink truncate">
                          {suggestion.name}
                          {suggestion.unit ? ` (${suggestion.unit})` : ''}
                        </span>
                      </span>
                      {price && (
                        <span className="text-label text-ink-mute flex-shrink-0">{price}</span>
                      )}
                    </label>
                  )
                })}
                <label className="flex items-center gap-3 px-2 py-2 rounded-xl hover:bg-ground cursor-pointer">
                  <input
                    type="radio"
                    name={`item-${current.id}`}
                    checked={selections[current.id] === undefined}
                    onChange={() => setSelections((prev) => ({ ...prev, [current.id]: undefined }))}
                    className="accent-marigold-deep h-4 w-4 flex-shrink-0"
                  />
                  <span className="text-body text-ink-soft">Skip this item</span>
                </label>
              </div>
            )}

            <div className="flex gap-2 mt-5">
              {index > 0 && (
                <button
                  type="button"
                  onClick={() => goTo(index - 1)}
                  disabled={step === 'submitting'}
                  className="pressable min-h-touch px-4 py-3 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground disabled:opacity-50"
                >
                  Back
                </button>
              )}
              <button
                type="button"
                onClick={isLast ? handleConfirm : () => goTo(index + 1)}
                disabled={step === 'submitting'}
                className="pressable flex-1 min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
              >
                {step === 'submitting' ? 'Exporting…' : isLast ? 'Confirm export' : 'Next'}
              </button>
            </div>
          </>
        )}

        {step === 'result' && result && (
          <div className="mt-4">
            <p className="text-body text-ink mb-4">
              Added {result.added}, skipped {result.skipped}.
            </p>

            {result.failures.length > 0 && (
              <div className="bg-marigold-faint rounded-xl p-4 mb-4">
                <h3 className="text-label font-semibold text-honey-deep uppercase tracking-wide mb-2">
                  Couldn&apos;t add
                </h3>
                <ul className="space-y-1">
                  {result.failures.map((failure) => (
                    <li key={failure.itemId} className="text-label text-ink-soft">
                      {itemNameFor(failure.itemId)} — {failure.reason}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <button
              type="button"
              onClick={onClose}
              className="pressable w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep"
            >
              Done
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
