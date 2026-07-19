/**
 * Fractional index arithmetic for CRDT sort keys — TypeScript mirror of the
 * Java FractionalIndex class (backend/src/main/java/com/shopmate/domain/crdt/FractionalIndex.java).
 * Both implementations must produce identical output for the same inputs —
 * pinned by shared/fractional-index-vectors.json, loaded by both the Vitest
 * and JUnit suites.
 *
 * Keys are lexicographically orderable strings over a base-36 alphabet
 * ('0'-'9' then 'a'-'z', digits sorting below letters). Ordering is always
 * plain string comparison (`<`, `>`, `.compareTo`) — never numeric parsing.
 *
 * BUG-8 background (see PLAN.md / docs/plans/section-grouping.md Phase 0):
 * the previous algorithm padded the shorter key with the alphabet's lowest
 * character ('a') to equal length before diffing. That collides whenever the
 * longer key equals the padded-short key exactly (e.g. `between("b0","b0a")`
 * pads "b0" to "b0a", which then equals `after` exactly) — the code fell
 * back to "keys are equal, invent a new suffix", producing a key that sorts
 * *after* `after`, violating the strictly-between contract. The fix below
 * never pads: a position past the end of a string is treated as a real
 * "no digit" sentinel (strictly below every real digit, including the
 * alphabet's own minimum), not as a stand-in for the minimum digit itself.
 */

const ALPHABET = '0123456789abcdefghijklmnopqrstuvwxyz'
const BASE = ALPHABET.length // 36

/** Default starting key for a brand-new, empty list. Not a hard algorithmic bound. */
export const MIN = 'a0'
/** Illustrative upper reference; the algorithm has no fixed ceiling. */
export const MAX = 'z0'

function digitOf(ch: string): number {
  const v = ALPHABET.indexOf(ch)
  if (v < 0) {
    throw new Error(`invalid fractional index character: '${ch}'`)
  }
  return v
}

function charOf(v: number): string {
  return ALPHABET[v]
}

const MAX_ITERATIONS = 10_000

/**
 * Returns the lexicographically-smallest key strictly greater than `lo` and
 * strictly less than `bound` (or unbounded above, if `bound` is null).
 *
 * `lo` is always a concrete (possibly empty) string; an empty string means
 * "no lower bound" — every position reads as the "ended" sentinel, which is
 * exactly what we want (it's always less than any real key).
 *
 * Walks the two keys position by position. A "no digit" sentinel (-1 for
 * `lo`, or the sentinel BASE for an unbounded `bound`) never collides with a
 * real digit, which is what makes tight pairs like ("b0", "b0a") solvable:
 * lo runs out at index 2 (sentinel -1), bound's digit there is 'a' (10), so
 * there's room below it (any digit 0-9) that both extends past lo (making
 * the result > lo, since lo is now a strict, shorter prefix) and sits below
 * bound's digit at that position (making the result < bound).
 *
 * Once a differing digit has been chosen at some position while both sides
 * were still real (the "gap of exactly 1" case), `bound` is set to null for
 * all later positions: that digit already resolved the comparison against
 * the original bound, so nothing further can push the result back above it.
 */
function midpointCore(lo: string, hiOrNull: string | null): string {
  let result = ''
  let bound = hiOrNull

  for (let i = 0; i < MAX_ITERATIONS; i++) {
    const a = i < lo.length ? digitOf(lo[i]) : -1
    const b = bound === null ? BASE : i < bound.length ? digitOf(bound[i]) : -1

    if (a === b) {
      if (a === -1) {
        // Both sides ended at the same position with an identical prefix so
        // far: either lo === hi (should already be rejected by the caller),
        // or hi === lo + (only the alphabet's minimum digit repeated). The
        // latter is a genuine mathematical dead end — no string can be
        // strictly between a key and "that same key plus only its lowest
        // possible digit" under plain lexicographic string comparison.
        throw new Error(
          `no key exists strictly between "${lo}" and "${hiOrNull}" — keys are too tight`,
        )
      }
      result += charOf(a)
      continue
    }

    // a < b is guaranteed here: lo < bound and every prior position matched.
    if (b - a >= 2) {
      const digit = a + Math.floor((b - a) / 2)
      if (digit === 0) {
        // digit 0 is the alphabet's global minimum. Returning here would
        // make the result exactly "lo + digit0" — the one pairing with zero
        // room for a *future* between() call against it (see the a===b/-1
        // branch above). We've already secured result < bound (0 < b at
        // this position), so bound no longer constrains anything further —
        // append the 0 as a non-final digit and keep going one more
        // position with bound unlocked, which always resolves safely.
        result += charOf(0)
        bound = null
        continue
      }
      return result + charOf(digit)
    }

    // b - a === 1: no room for a distinct digit at this position.
    if (a === -1) {
      // lo ended, bound's digit here is exactly 0 (the alphabet minimum) —
      // tie on it. lo stays "ended" (sentinel -1) at every later position
      // automatically; bound keeps constraining us since we haven't yet
      // produced anything strictly below it.
      result += charOf(0)
    } else {
      // Both real, adjacent digits — tie on the lower one. That digit alone
      // already guarantees result < original bound, so bound no longer
      // constrains anything after this position.
      result += charOf(a)
      bound = null
    }
  }

  throw new Error(`fractional index exceeded ${MAX_ITERATIONS} iterations`)
}

/**
 * Returns a key strictly between `before` and `after` (lexicographic order).
 * Pass null for before/after to mean "beginning" or "end" of the list.
 * Throws if `before >= after`.
 */
export function between(before: string | null, after: string | null): string {
  if (before !== null && after !== null && before >= after) {
    throw new Error(`before (${before}) must be less than after (${after})`)
  }
  return midpointCore(before ?? '', after)
}

/**
 * Generate a sort key for a new item appended at the end. Only the first
 * character is ever inspected: incrementing it (base-36) always produces a
 * key greater than any key already using that or a lower first character —
 * exactly the invariant the caller relies on (`lastKey` is the maximum
 * active sort key). Falls back to extending the key when the first
 * character is already at the top of the alphabet; any nonempty suffix
 * makes the result greater than `lastKey` (a longer string with `lastKey` as
 * a strict prefix always sorts after it).
 */
export function append(lastKey: string | null): string {
  if (lastKey === null) return 'a0'
  const first = digitOf(lastKey[0])
  if (first < BASE - 1) {
    return charOf(first + 1) + '0'
  }
  return lastKey + 'i0'
}
