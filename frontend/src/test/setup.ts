import '@testing-library/jest-dom'

// Node >= 22 ships its own `localStorage` global which shadows jsdom's Storage
// inside the vitest worker and does not implement the Web Storage API there.
// Install a real in-memory implementation so code using localStorage works.
class MemoryStorage implements Storage {
  private store = new Map<string, string>()

  get length(): number {
    return this.store.size
  }

  clear(): void {
    this.store.clear()
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? this.store.get(key)! : null
  }

  key(index: number): string | null {
    return [...this.store.keys()][index] ?? null
  }

  removeItem(key: string): void {
    this.store.delete(key)
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value))
  }
}

if (typeof globalThis.localStorage?.getItem !== 'function') {
  const storage = new MemoryStorage()
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true,
    writable: true,
  })
  if (typeof window !== 'undefined' && (window as unknown) !== globalThis) {
    Object.defineProperty(window, 'localStorage', { value: storage, configurable: true })
  }
}
