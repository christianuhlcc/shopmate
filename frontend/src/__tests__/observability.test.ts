import { beforeEach, describe, expect, it, vi } from 'vitest'
import { init } from '@dash0/sdk-web'
import { initObservability, shouldEnableRum } from '../observability'

vi.mock('@dash0/sdk-web', () => ({ init: vi.fn() }))

describe('shouldEnableRum', () => {
  it('is true for a prod build served over https', () => {
    expect(shouldEnableRum(true, 'https:')).toBe(true)
  })

  it('is false for dev builds', () => {
    expect(shouldEnableRum(false, 'https:')).toBe(false)
  })

  it('is false over plain http (local docker compose)', () => {
    expect(shouldEnableRum(true, 'http:')).toBe(false)
  })
})

describe('initObservability', () => {
  beforeEach(() => {
    vi.mocked(init).mockClear()
  })

  it('does nothing when disabled', () => {
    initObservability(false)
    expect(init).not.toHaveBeenCalled()
  })

  it('initializes the Dash0 SDK against the same-origin telemetry proxy', () => {
    initObservability(true)
    expect(init).toHaveBeenCalledWith(
      expect.objectContaining({
        serviceName: 'shopmate-frontend',
        endpoint: expect.objectContaining({
          url: `${window.location.origin}/telemetry`,
        }),
      }),
    )
  })
})
