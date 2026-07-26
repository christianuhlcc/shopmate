import { useEffect, useState } from 'react'
import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

type ItemSuggestions = components['schemas']['ItemSuggestions']
type ExportResult = components['schemas']['ExportResult']

interface PicnicExportSheetProps {
  listId: string
  onClose: () => void
  onNeedsCredentials: () => void
}

// Small internal state machine — this is one component with state
// transitions, not separate "suggestions" and "confirm/result" components.
type Step = 'loading' | 'error' | 'picking' | 'submitting' | 'result'

const CREDENTIALS_MISSING_MESSAGE = 'Link a Picnic account to export this list.'
const GENERIC_ERROR = "Picnic isn't available right now — try again later."

function formatPrice(priceCents?: number | null): string | null {
  if (priceCents === undefined || priceCents === null) return null
  return (priceCents / 100).toFixed(2) + ' €'
}

/**
 * Bottom sheet that exports a list's active items to a Picnic cart. Reuses
 * the sheet-backdrop/sheet-panel idiom from GroupSheet. Drives itself
 * through a single step machine: fetch per-item suggestions, let the user
 * pick an article (or skip) per item, submit the selections, show the
 * result. Any credentials/availability failure — on either the initial
 * fetch or the final submit — is funneled through the same error branch.
 */
export function PicnicExportSheet({ listId, onClose, onNeedsCredentials }: PicnicExportSheetProps) {
  const [step, setStep] = useState<Step>('loading')
  const [items, setItems] = useState<ItemSuggestions[]>([])
  const [selections, setSelections] = useState<Record<string, string | undefined>>({})
  const [credentialsMissing, setCredentialsMissing] = useState(false)
  const [result, setResult] = useState<ExportResult | null>(null)

  useEffect(() => {
    let cancelled = false
    apiClient
      .POST('/lists/{listId}/picnic/suggestions', { params: { path: { listId } } })
      .then(({ data, error: apiError }) => {
        if (cancelled) return
        if (apiError || !data) {
          setCredentialsMissing(apiError?.code === 'PICNIC_CREDENTIALS_MISSING')
          setStep('error')
          return
        }
        setItems(data.items)
        // Default selection per item: its first suggestion, or "skip" (undefined)
        // when there are none.
        setSelections(
          Object.fromEntries(data.items.map((item) => [item.itemId, item.suggestions[0]?.id])),
        )
        setStep('picking')
      })
      .catch(() => {
        if (cancelled) return
        setCredentialsMissing(false)
        setStep('error')
      })
    return () => {
      cancelled = true
    }
  }, [listId])

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
          itemId: item.itemId,
          articleId: selections[item.itemId],
        })),
      },
    })
    if (apiError || !data) {
      setCredentialsMissing(apiError?.code === 'PICNIC_CREDENTIALS_MISSING')
      setStep('error')
      return
    }
    setResult(data)
    setStep('result')
  }

  function itemNameFor(itemId: string): string {
    return items.find((item) => item.itemId === itemId)?.itemName ?? itemId
  }

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
        <h2 id="picnic-export-sheet-title" className="text-title font-semibold text-ink mb-1">
          Export to Picnic
        </h2>
        <span className="inline-block mb-3 text-label font-semibold text-honey-deep bg-marigold-tint rounded-full px-2 py-0.5">
          Beta — uses an unofficial Picnic API
        </span>

        {step === 'loading' && (
          <div role="status" aria-label="Loading suggestions" className="space-y-3 mt-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="space-y-2">
                <div className="h-4 w-2/5 rounded bg-line animate-pulse" />
                <div className="h-3 w-3/5 rounded bg-line/70 animate-pulse" />
              </div>
            ))}
          </div>
        )}

        {step === 'error' &&
          (credentialsMissing ? (
            <div className="mt-4">
              <p className="text-body text-ink-soft mb-4">{CREDENTIALS_MISSING_MESSAGE}</p>
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

        {(step === 'picking' || step === 'submitting') && (
          <>
            <ul className="divide-y divide-line -mx-2 mb-4">
              {items.map((item) => (
                <li key={item.itemId} className="px-2 py-3">
                  <h3 className="text-body font-semibold text-ink mb-2">{item.itemName}</h3>

                  {item.suggestions.length === 0 ? (
                    <p className="text-label text-ink-mute">
                      No matches found — this item will be skipped.
                    </p>
                  ) : (
                    <div
                      role="radiogroup"
                      aria-label={`Choose a Picnic product for ${item.itemName}`}
                      className="space-y-1"
                    >
                      {item.suggestions.map((suggestion) => {
                        const price = formatPrice(suggestion.priceCents)
                        return (
                          <label
                            key={suggestion.id}
                            className="flex items-center gap-3 px-2 py-2 rounded-xl hover:bg-ground cursor-pointer"
                          >
                            <input
                              type="radio"
                              name={`item-${item.itemId}`}
                              checked={selections[item.itemId] === suggestion.id}
                              onChange={() =>
                                setSelections((prev) => ({ ...prev, [item.itemId]: suggestion.id }))
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
                              <span
                                aria-hidden="true"
                                className="w-8 h-8 rounded bg-line flex-shrink-0"
                              />
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
                          name={`item-${item.itemId}`}
                          checked={selections[item.itemId] === undefined}
                          onChange={() =>
                            setSelections((prev) => ({ ...prev, [item.itemId]: undefined }))
                          }
                          className="accent-marigold-deep h-4 w-4 flex-shrink-0"
                        />
                        <span className="text-body text-ink-soft">Skip this item</span>
                      </label>
                    </div>
                  )}
                </li>
              ))}
            </ul>

            <button
              type="button"
              onClick={handleConfirm}
              disabled={step === 'submitting'}
              className="pressable w-full min-h-touch px-4 py-3 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50"
            >
              {step === 'submitting' ? 'Exporting…' : 'Confirm export'}
            </button>
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
