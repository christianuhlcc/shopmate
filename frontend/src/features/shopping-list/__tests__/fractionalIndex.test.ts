import { describe, it, expect } from 'vitest'
import { between, append } from '../utils/fractionalIndex'

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

  it('supports repeated bisection (20 iterations)', () => {
    let lo = 'a0'
    let hi = 'z0'
    for (let i = 0; i < 20; i++) {
      const mid = between(lo, hi)
      expect(mid > lo).toBe(true)
      expect(mid < hi).toBe(true)
      hi = mid
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

  it('throws when before >= after', () => {
    expect(() => between('z0', 'a0')).toThrow()
    expect(() => between('m0', 'm0')).toThrow()
  })

  it('append overflows past z by extending the key', () => {
    const k = append('z0')
    expect(k > 'z0').toBe(true)
    expect(k).toBe('z0m0')
  })

  it('mirrors the Java fallback for keys equal after padding', () => {
    // Same output as the backend FractionalIndex for this degenerate input
    expect(between('b', 'ba')).toBe('bam')
  })
})
