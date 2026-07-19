# ADR-0006: Server-Sent Events (not WebSockets) for real-time sync

Date: 2026-04-28 · Status: Accepted

## Context

Clients need to see other members' edits live. All mutations already go
through REST (`PATCH` with a CRDT change), so the real-time channel only has
to push server→client. WebSockets would add a second bidirectional protocol,
its own auth story, and proxy/upgrade handling for a direction we don't need.

## Decision

Use **SSE** (`GET /lists/{id}/events`, Spring `SseEmitter`): one stream per
list, broadcasting item changes to subscribed members. Writes stay on REST.
Plain HTTP means the nginx edge proxy needs only buffering disabled, and
`EventSource` reconnects automatically.

Because `EventSource` cannot set headers, the stream authenticates with a
**separate short-lived JWT** scoped to `(userId, listId)`, 15-min TTL,
fetched via `GET /lists/{id}/sse-token` and passed as `?token=` — the main
session JWT never appears in a URL.

## Consequences

- One protocol (HTTP) end to end; simple proxying, built-in reconnect.
- No client→server push channel; fine, since mutations are REST calls.
- A token does appear in a URL (access logs), mitigated by short TTL and
  narrow `(userId, listId)` scope.
