import createClient from 'openapi-fetch'
import type { paths } from './schema'

// Module-level token ref — avoids circular dependency with AuthContext
let _token: string | null = null

export function setApiToken(token: string | null) {
  _token = token
}

export const apiClient = createClient<paths>({ baseUrl: '' })

apiClient.use({
  async onRequest({ request }) {
    if (_token) {
      request.headers.set('Authorization', `Bearer ${_token}`)
    }
    return request
  },
})
