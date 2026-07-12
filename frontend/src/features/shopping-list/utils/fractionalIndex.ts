/**
 * Fractional index arithmetic for CRDT sort keys — TypeScript mirror of the
 * Java FractionalIndex class (backend/src/main/java/com/shopmate/domain/crdt/FractionalIndex.java).
 * Both implementations must produce identical output for the same inputs.
 *
 * Keys are lexicographically orderable strings: base-26 characters (a-z) with
 * a digit suffix for disambiguation (Figma/Linear-style fractional indexing).
 */

export const MIN = 'a0'
export const MAX = 'z0'

/**
 * Returns a key strictly between `before` and `after` (lexicographic order).
 * Pass null for before/after to mean "beginning" or "end" of the list.
 * Throws if `before >= after`.
 */
export function between(before: string | null, after: string | null): string {
  const lo = before ?? MIN
  const hi = after ?? MAX

  if (lo >= hi) {
    throw new Error(`before (${lo}) must be less than after (${hi})`)
  }

  return midpoint(lo, hi)
}

/** Generate a sort key for a new item appended at the end. */
export function append(lastKey: string | null): string {
  if (lastKey === null) return 'a0'
  // Increment the first character
  const first = lastKey.charCodeAt(0)
  if (first < 'z'.charCodeAt(0)) {
    return String.fromCharCode(first + 1) + '0'
  }
  // Overflow: append a suffix
  return lastKey + 'm0'
}

function midpoint(lo: string, hi: string): string {
  // Pad shorter string with 'a' (lowest character) to equal length
  const maxLen = Math.max(lo.length, hi.length)
  const a = lo.padEnd(maxLen, 'a')
  const b = hi.padEnd(maxLen, 'a')

  let mid = ''
  for (let i = 0; i < maxLen; i++) {
    const ca = a.charCodeAt(i)
    const cb = b.charCodeAt(i)
    const diff = cb - ca
    if (diff > 1) {
      // Found a gap — take the midpoint character
      return mid + String.fromCharCode(ca + Math.floor(diff / 2))
    } else if (diff === 1) {
      // Gap of 1 — take the lower character and recurse into suffix
      mid += a[i]
      // Append midpoint suffix between end-of-lo and end-of-hi
      const loSuffix = lo.length > i + 1 ? lo.slice(i + 1) : 'a'
      return mid + midpoint(loSuffix, 'zz')
    } else {
      // diff === 0: characters are equal, keep going
      mid += a[i]
    }
  }
  // Strings are equal after padding — append a midpoint suffix
  return mid + midpoint('a', 'z')
}
