import { init } from '@dash0/sdk-web'

/**
 * RUM is only wanted on the real deployment: a prod bundle served over https.
 * The local docker compose stack serves the same prod bundle over plain http
 * on localhost (and has no collector), so the protocol check keeps it off.
 */
export function shouldEnableRum(
  isProdBuild: boolean = import.meta.env.PROD,
  protocol: string = window.location.protocol,
): boolean {
  return isProdBuild && protocol === 'https:'
}

export function initObservability(enabled: boolean = shouldEnableRum()): void {
  if (!enabled) return
  init({
    serviceName: 'shopmate-frontend',
    serviceNamespace: 'shopmate',
    environment: 'production',
    endpoint: {
      // Same-origin proxy: nginx forwards /telemetry/* to the OTel collector,
      // which attaches the real Dash0 auth token server-side. The SDK requires
      // an authToken value, but this placeholder grants nothing.
      url: `${window.location.origin}/telemetry`,
      authToken: 'proxied-by-collector',
    },
  })
}
