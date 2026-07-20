# ADR-0012: Section classification via bundled dictionary + per-list learned corrections

Date: 2026-07-19 · Status: Accepted

## Context

Items should group by supermarket section (Obst & Gemüse, Molkerei, Tiefkühl, …)
in store-walk order, so the shopper crosses the store in one pass. Item names
are German only. The section set is a fixed app-defined taxonomy (14 sections).
Hard constraint: no per-item LLM API call at runtime — classification must be
effectively free.

Options considered:

- **A. Bundled German keyword dictionary.** Curated TSV (`term → section
  code`) shipped as a classpath resource, ~1,000–1,200 entries (~35 KB):
  base nouns plus plural/synonym/regional variants. Matching must handle
  German morphology: normalize (lowercase, ß→ss, strip quantity tokens),
  exact lookup, plural-stem retries, then longest-suffix compound match —
  German compounds are head-final, so "Vollkornbrot" resolves via "brot",
  "Kirschtomaten" via "tomaten". Zero runtime cost. ~85–90% accuracy on
  household lists; misses are permanent without B.
- **B. A + learned corrections.** Every manual section reassignment is stored
  as `(list, normalized name) → section`; resolution is learned map →
  dictionary → `SONSTIGES`. Household lists are highly repetitive
  (~100–200 recurring items), so accuracy converges toward 100% where it
  matters. Cost: one small table and one outbound port.
- **C. Offline batch LLM enrichment.** Periodic job classifies accumulated
  unmatched terms to grow the dictionary. Pennies per month and respects the
  constraint, but adds a job, an API key, and quality control for marginal
  gain once B converges.
- **D. Local embedding model (ONNX MiniLM).** ~100+ MB RSS with runtime and
  tokenizer on a t4g.small (2 GB) already running Postgres, Spring Boot,
  nginx, and the OTel collector — and embeddings underperform lexical
  matching on short single-noun German terms. Rejected.

## Decision

Adopt **B**: bundled dictionary + per-list learned corrections. C remains a
possible future extension writing into the same corrections store.

- **`section` becomes a sixth LWW register** on `ShoppingItem` (string
  section code). A correction — including a cross-section drag — is an
  ordinary `SECTION` change; CRDT convergence needs no new machinery
  (same trade-off class as ADR-0002).
- **Classification runs server-side at item creation.** The server is already
  the single write authority (server-assigned timestamps, ADR-0002); the
  initial section propagates through the existing per-field SSE fan-out.
  Clients never classify.
- **Hexagonal placement:** the `Section` taxonomy (enum with walk order) and
  the dictionary classifier are pure domain code (`domain/section/`); the TSV
  loads from the classpath with plain `java.io` — ArchUnit-clean. The learned
  map is an outbound port (`SectionCorrectionRepository`) with a JPA adapter.
- **Learned-map scope is per-list**, keyed `(list_id, normalized_name)`, LWW
  upsert on concurrent corrections. There is no household entity; the shared
  list is the closest proxy — membership already gates authorization, and
  corrections cannot leak between unrelated lists.
- **Taxonomy:** 14 codes in default walk order, `SONSTIGES` as fallback,
  always last. Stored as strings, not DB enums, so taxonomy evolution is a
  code change; unknown codes render as `SONSTIGES`.

## Consequences

- Zero runtime cost and no new infrastructure; classification is a map lookup.
- First-pass accuracy ~85–90%, converging near 100% per list on recurring
  items via corrections.
- The dictionary is a curated artifact needing an initial quality pass and a
  representative basket test; gaps surface as `SONSTIGES` and self-heal.
- Old frontend bundles ignore unknown `SECTION` SSE events harmlessly; deploy
  backend before the section UI to avoid 400s on `SECTION` PATCHes.
