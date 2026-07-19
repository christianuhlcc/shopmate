import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, it, expect } from 'vitest'
import { between, append } from '../utils/fractionalIndex'

// Shared cross-language vectors: the JUnit suite
// (backend/src/test/java/com/shopmate/domain/crdt/FractionalIndexTest.java)
// loads the exact same file and asserts the exact same outputs. This pins
// the Java and TS implementations together so they can never silently
// re-diverge (BUG-8).
const __dirname = path.dirname(fileURLToPath(import.meta.url))
const vectorsPath = path.resolve(__dirname, '../../../../../shared/fractional-index-vectors.json')
const vectors = JSON.parse(readFileSync(vectorsPath, 'utf-8')) as {
  append: Array<{ last: string | null; expect: string; note?: string }>
  between: Array<{ before: string | null; after: string | null; expect: string; note?: string }>
  tooTight: Array<{ before: string; after: string; note?: string }>
  bisectionNarrowingHi: { lo: string; initialHi: string; keys: string[] }
  bisectionNarrowingLo: { initialLo: string; hi: string; keys: string[] }
}

describe('fractionalIndex — shared vectors', () => {
  it.each(vectors.append)('append($last) === $expect', ({ last, expect: exp }) => {
    expect(append(last)).toBe(exp)
  })

  it.each(vectors.between)('between($before, $after) === $expect', ({ before, after, expect: exp }) => {
    expect(between(before, after)).toBe(exp)
  })

  it.each(vectors.tooTight)('between($before, $after) throws (too tight)', ({ before, after }) => {
    expect(() => between(before, after)).toThrow()
  })

  it('reproduces the pinned narrowing-hi bisection chain exactly', () => {
    const { lo, initialHi, keys } = vectors.bisectionNarrowingHi
    let hi = initialHi
    for (const expectedKey of keys) {
      const mid = between(lo, hi)
      expect(mid).toBe(expectedKey)
      expect(mid > lo).toBe(true)
      expect(mid < hi).toBe(true)
      hi = mid
    }
  })

  it('reproduces the pinned narrowing-lo bisection chain exactly', () => {
    const { initialLo, hi, keys } = vectors.bisectionNarrowingLo
    let lo = initialLo
    for (const expectedKey of keys) {
      const mid = between(lo, hi)
      expect(mid).toBe(expectedKey)
      expect(mid > lo).toBe(true)
      expect(mid < hi).toBe(true)
      lo = mid
    }
  })
})

describe('fractionalIndex', () => {
  it('between null and key returns key less than after', () => {
    const k = between(null, 'm0')
    expect(k < 'm0').toBe(true)
  })

  it('between two keys returns key strictly between them', () => {
    const mid = between('a0', 'z0')
    expect(mid > 'a0').toBe(true)
    expect(mid < 'z0').toBe(true)
  })

  it('between adjacent characters produces a valid key', () => {
    const mid = between('a0', 'b0')
    expect(mid > 'a0').toBe(true)
    expect(mid < 'b0').toBe(true)
  })

  it('supports repeated bisection (20 iterations) narrowing from both sides', () => {
    let lo = 'a0'
    let hi = 'z0'
    for (let i = 0; i < 20; i++) {
      const mid = between(lo, hi)
      expect(mid > lo).toBe(true)
      expect(mid < hi).toBe(true)
      if (i % 2 === 0) hi = mid
      else lo = mid
    }
  })

  it('between key and null returns key greater than before', () => {
    const k = between('m0', null)
    expect(k > 'm0').toBe(true)
  })

  it('append produces strictly increasing keys', () => {
    const k1 = append(null)
    const k2 = append(k1)
    const k3 = append(k2)
    expect(k1 < k2).toBe(true)
    expect(k2 < k3).toBe(true)
  })

  it('append is order-safe against a longer, previously-bisected key', () => {
    // Simulates: item gets reordered (sortKey becomes a between()-generated
    // multi-char key), then a brand new item is appended after it.
    const reordered = between('a0', 'z0') // e.g. "m"
    const appended = append(reordered)
    expect(appended > reordered).toBe(true)
  })

  it('throws when before >= after', () => {
    expect(() => between('z0', 'a0')).toThrow()
    expect(() => between('m0', 'm0')).toThrow()
  })

  it('append overflows past the alphabet max by extending the key', () => {
    const k = append('z0')
    expect(k > 'z0').toBe(true)
  })

  it('the b0/b0a regression case no longer produces a key that sorts after "after"', () => {
    const mid = between('b0', 'b0a')
    expect(mid > 'b0').toBe(true)
    expect(mid < 'b0a').toBe(true)
  })

  it('throws a descriptive error for a too-tight pair instead of returning a wrong key', () => {
    expect(() => between('b0', 'b00')).toThrow()
  })

  it('rejects invalid characters', () => {
    expect(() => between('b0', 'b0!')).toThrow()
  })
})

describe('fractionalIndex — property test (lighter mirror of the JUnit property test)', () => {
  it('holds the strictly-between contract across many random pairs and bisection depths', () => {
    const chars = '0123456789abcdefghijklmnopqrstuvwxyz'
    function randomKey(): string {
      const len = 1 + Math.floor(Math.random() * 4)
      let s = ''
      for (let i = 0; i < len; i++) s += chars[Math.floor(Math.random() * chars.length)]
      return s
    }

    let violations = 0
    for (let trial = 0; trial < 200; trial++) {
      let a = randomKey()
      let b = randomKey()
      while (a === b) b = randomKey()
      let lo: string
      let hi: string
      if (a < b) {
        lo = a
        hi = b
      } else {
        lo = b
        hi = a
      }

      const depth = 1 + Math.floor(Math.random() * 12)
      for (let d = 0; d < depth; d++) {
        let mid: string
        try {
          mid = between(lo, hi)
        } catch {
          // A too-tight pair is a legitimate terminal state (see tooTight
          // vector); stop narrowing this trial.
          break
        }
        if (!(mid > lo && mid < hi)) {
          violations++
          break
        }
        expect(mid.length).toBeLessThan(60) // keys stay modest, not unbounded
        if (Math.random() < 0.5) hi = mid
        else lo = mid
      }
    }
    expect(violations).toBe(0)
  })
})
