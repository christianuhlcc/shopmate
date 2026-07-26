# ADR-0015: Generic `GroceryExportPort` for multiple grocery-export adapters

Date: 2026-07-26 · Status: Accepted · Supersedes: parts of ADR-0014

## Context

ADR-0014 designed the Picnic export beta with a Picnic-specific port
(`PicnicClientPort`), a Picnic-specific credential table
(`picnic_credentials`), and Picnic-specific endpoints. No code exists for it
yet.

More exporters are planned, and they will share the same shape as Picnic:
no stable official API (reverse-engineered or a thin unofficial one),
freitext-to-catalog article mapping that needs a human confirmation step
(ADR-0014's top-5 picker), and a personal per-user credential rather than
app-level secrets. Hexagonal architecture (ADR-0003) exists precisely to let
a stable port carry multiple interchangeable adapters — that only pays off
if the port is shaped for more than the first adapter.

Options:

- **A. Ship the Picnic-specific design from ADR-0014, generalize once a
  second exporter is actually needed.** The usual YAGNI argument, but the
  shared shape here isn't speculative — it's the stated, confirmed direction
  ("wir bauen bestimmt mehr Exporte"). Generalizing later means a schema
  migration on credentials and a breaking change to endpoints that would
  already be shipped. Rejected: the premature-abstraction cost is lower than
  the migrate-later cost in this specific case.
- **B. Generalize now, before the first adapter is coded.** One
  `GroceryExportPort`, one credential table keyed by provider, endpoints
  parametrized by `{provider}`; Picnic becomes the first concrete adapter
  instead of the shape of the whole feature. Chosen.

## Decision

- **Outbound port** `domain/port/out/GroceryExportPort`: provider-agnostic
  `searchArticles(credentials, term)` / `addToCart(credentials, articleId,
  count)`. A `GroceryProvider` enum names the concrete grocer (`PICNIC` for
  now); a sealed `GroceryCredentials` interface holds provider-specific
  credential shapes (`PicnicCredentials(email, passwordMd5)` is the first
  variant). All plain records/interfaces in `domain/model` — no framework
  imports, ArchUnit-clean.
- **`ArticleSuggestion`** (id, name, imageUrl, priceCents, unit) is the
  canonical, provider-agnostic result shape. Translating a provider's raw
  response into it is the adapter's job — that's the pattern.
- **Multiple adapters, one lookup.** Each exporter is a Spring bean
  implementing `GroceryExportPort` (`adapter/out/picnic/PicnicGroceryAdapter`
  first). `infrastructure/config` assembles a `Map<GroceryProvider,
  GroceryExportPort>` from the injected `List<GroceryExportPort>`. The
  application service depends on that `Map` — plain `java.util`, so the
  "application depends only on domain, `java.*`, `org.slf4j.*`" rule
  (CLAUDE.md) still holds — and resolves the adapter per request.
- **One credential table**: `grocery_credentials(user_id, provider,
  payload_encrypted, created_at, updated_at)`, PK `(user_id, provider)`.
  `payload_encrypted` is a provider-specific JSON blob encrypted at rest;
  Picnic's is `{email, passwordMd5}`. The encryption key env var is
  `GROCERY_CREDENTIAL_ENC_KEY` (ADR-0014 named it `PICNIC_CREDENTIAL_ENC_KEY`
  — renamed here at no migration cost since nothing was built against it).
- **Inbound port/service generalize**: `GroceryExportUseCase` /
  `GroceryExportService` take a `GroceryProvider` parameter. A provider with
  no registered adapter throws `UnsupportedGroceryProviderException` → 4xx —
  a real rollout case (config enables a provider before its adapter bean
  ships, or vice versa), not defensive dead code.
- **Endpoints take `{provider}` as a path segment** (OpenAPI enum, one value
  today: `PICNIC`): `POST /api/lists/{listId}/export/{provider}/suggestions`,
  `POST /api/lists/{listId}/export/{provider}/confirm`,
  `PUT/DELETE /api/users/me/export-credentials/{provider}`. A new exporter is
  an enum value plus an adapter bean, not a new endpoint family.
- **Feature flag becomes an allow-list**:
  `shopmate.grocery-export.enabled-providers` (default empty), so providers
  can be beta-gated independently instead of one global boolean.
- **Frontend gets one generic export flow** (provider picker → per-item
  top-5 suggestion picker → confirm) rather than a Picnic-specific screen. A
  second exporter is a new entry in the provider picker, not new UI.
- **Everything ADR-0014 decided about the *Picnic adapter itself* stands**:
  Java re-implementation of the unofficial endpoints (not a sidecar, not
  client-side calls), the top-5 manual picker, per-user (not per-group)
  credentials, MD5 login mechanics, Germany-only, no session caching, and no
  persisted learned mapping yet. Those become the concrete shape of the
  `PICNIC` adapter rather than the shape of the feature.

## Consequences

- One layer of indirection (provider enum + map lookup) exists for what is,
  today, a single adapter. Accepted given the stated intent to add more —
  this generalization is paid for now specifically because it's known to be
  needed soon, not on spec.
- Credential encryption/storage is written once, generically, instead of
  being reshaped for every new exporter.
- `GroceryCredentials` as a sealed interface makes every new provider add a
  compile-time-enforced variant (serialization, decryption) instead of a
  runtime surprise — same fallback discipline as the `Section` taxonomy in
  ADR-0012 (unknown case must be handled explicitly, not silently ignored).
- The `.../export/{provider}/...` endpoint shape is now committed to before
  a second provider exists to validate it against. If a future exporter
  can't fit this shape (e.g. no cart concept, or a multi-step OAuth instead
  of a stored credential), that needs its own ADR rather than forcing the
  fit.
- ADR-0014's Picnic-specific port/table/endpoint names no longer apply as
  written; its adapter-level decisions (client behavior, mapping UX,
  credential mechanics, scope limits) are unchanged and now describe the
  `PICNIC` `GroceryExportPort` implementation.
