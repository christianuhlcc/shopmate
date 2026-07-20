import { useEffect, useState } from 'react'
import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

type GroupResponse = components['schemas']['GroupResponse']
type InviteType = components['schemas']['InviteType']
type InviteCodeResponse = components['schemas']['InviteCodeResponse']

interface GroupSheetProps {
  onClose: () => void
}

const INVITE_LABELS: Record<InviteType, string> = {
  JOIN_GROUP: 'Invite to this group',
  NEW_GROUP: 'Invite for a new group',
}

/**
 * Bottom sheet showing the caller's group, its members, and invite-code
 * generation. Reuses the sheet-backdrop/sheet-panel idiom from SectionSheet.
 */
export function GroupSheet({ onClose }: GroupSheetProps) {
  const [group, setGroup] = useState<GroupResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [invite, setInvite] = useState<InviteCodeResponse | null>(null)
  const [inviteError, setInviteError] = useState<string | null>(null)
  const [invitingType, setInvitingType] = useState<InviteType | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    apiClient
      .GET('/groups/me')
      .then(({ data, error: apiError }) => {
        if (apiError || !data) {
          setError('Could not load your group.')
          return
        }
        setGroup(data)
      })
      .catch(() => setError('Could not load your group.'))
      .finally(() => setIsLoading(false))
  }, [])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  async function handleInvite(type: InviteType) {
    setInvitingType(type)
    setInviteError(null)
    setCopied(false)
    const { data, error: apiError } = await apiClient.POST('/invites', {
      body: { type },
    })
    setInvitingType(null)
    if (apiError || !data) {
      setInviteError('Could not create an invite code.')
      return
    }
    setInvite(data)
  }

  async function handleCopy() {
    if (!invite) return
    await navigator.clipboard.writeText(invite.code)
    setCopied(true)
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
        aria-labelledby="group-sheet-title"
        className="sheet-panel bg-panel rounded-2xl shadow-xl p-6 w-full max-w-sm z-sheet max-h-[80vh] overflow-y-auto"
      >
        <h2 id="group-sheet-title" className="text-title font-semibold text-ink mb-1">
          Your group
        </h2>

        {isLoading && (
          <div role="status" aria-label="Loading group" className="space-y-2 mt-4">
            <div className="h-4 w-2/5 rounded bg-line animate-pulse" />
            <div className="h-3 w-3/5 rounded bg-line/70 animate-pulse" />
          </div>
        )}

        {error && (
          <div className="bg-danger-tint border border-danger/25 rounded-xl p-4 mt-4 text-danger text-body">
            {error}
          </div>
        )}

        {group && (
          <>
            <p className="text-label text-ink-soft mb-4">{group.name}</p>

            <h3 className="text-label font-semibold text-ink-mute uppercase tracking-wide mb-2">
              Members · {group.members.length}
            </h3>
            <ul className="divide-y divide-line -mx-2 mb-6">
              {group.members.map((member) => (
                <li key={member.id} className="flex items-center gap-3 px-2 py-2">
                  <span
                    aria-hidden="true"
                    className="flex-shrink-0 w-8 h-8 rounded-full bg-marigold-tint text-honey-deep text-label font-semibold flex items-center justify-center"
                  >
                    {member.displayName.slice(0, 1).toUpperCase()}
                  </span>
                  <span className="min-w-0">
                    <span className="block text-body font-semibold text-ink truncate">
                      {member.displayName}
                    </span>
                    <span className="block text-label text-ink-mute truncate">
                      {member.email}
                    </span>
                  </span>
                </li>
              ))}
            </ul>

            <div className="space-y-2">
              {(Object.keys(INVITE_LABELS) as InviteType[]).map((type) => (
                <button
                  key={type}
                  type="button"
                  onClick={() => handleInvite(type)}
                  disabled={invitingType !== null}
                  className="pressable w-full min-h-touch px-4 py-2.5 bg-marigold text-ink rounded-full text-body font-semibold hover:bg-marigold-deep disabled:opacity-50 disabled:hover:bg-marigold"
                >
                  {invitingType === type ? 'Creating…' : INVITE_LABELS[type]}
                </button>
              ))}
            </div>

            {inviteError && (
              <p className="text-danger text-label mt-3">{inviteError}</p>
            )}

            {invite && (
              <div className="mt-4 bg-marigold-faint rounded-xl p-4 text-center">
                <p className="text-label text-honey-deep font-semibold">
                  {INVITE_LABELS[invite.type]}
                </p>
                <p
                  className="text-display text-ink mt-2 font-mono tracking-[0.35em]"
                  aria-label={`Invite code ${invite.code.split('').join(' ')}`}
                >
                  {invite.code}
                </p>
                <p className="text-label text-ink-soft mt-2">
                  Expires {new Date(invite.expiresAt).toLocaleString()}
                </p>
                <button
                  type="button"
                  onClick={handleCopy}
                  className="pressable mt-3 min-h-touch px-4 py-2 border border-line rounded-full text-body font-semibold text-ink-soft hover:bg-ground"
                >
                  {copied ? 'Copied!' : 'Copy code'}
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
