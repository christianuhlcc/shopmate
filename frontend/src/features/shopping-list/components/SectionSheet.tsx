import { useEffect } from 'react'
import { SECTIONS } from '../utils/sections'

interface SectionSheetProps {
  currentSection: string
  onSelect: (code: string) => void
  onClose: () => void
}

/**
 * Bottom sheet listing all 14 sections, current one highlighted — the
 * accessible, non-drag path to reassign an item's section. Reuses the
 * sheet-backdrop/sheet-panel idiom from ShoppingListPage's share sheet.
 */
export function SectionSheet({ currentSection, onSelect, onClose }: SectionSheetProps) {
  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

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
        aria-labelledby="section-sheet-title"
        className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet max-h-[80vh] overflow-y-auto"
      >
        <h2 id="section-sheet-title" className="text-title font-semibold text-ink mb-4">
          Move to section
        </h2>
        <ul className="divide-y divide-line -mx-2">
          {SECTIONS.map((section) => {
            const isCurrent = section.code === currentSection
            return (
              <li key={section.code}>
                <button
                  type="button"
                  onClick={() => onSelect(section.code)}
                  aria-current={isCurrent ? 'true' : undefined}
                  className={`pressable w-full text-left min-h-touch px-2 py-3 rounded-xl text-body font-semibold flex items-center justify-between gap-3 ${
                    isCurrent ? 'text-honey-deep bg-marigold-faint' : 'text-ink hover:bg-ground'
                  }`}
                >
                  <span className="truncate">{section.label}</span>
                  {isCurrent && (
                    <svg
                      className="w-4 h-4 flex-shrink-0"
                      viewBox="0 0 14 14"
                      fill="none"
                      aria-hidden="true"
                    >
                      <path
                        d="M3 7.5l2.8 2.8L11 4.5"
                        stroke="currentColor"
                        strokeWidth="2.25"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      />
                    </svg>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
