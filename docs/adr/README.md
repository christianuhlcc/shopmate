# Architecture Decision Records

Nygard-format ADRs, one per decision. ADR-0002 onward were backfilled on
2026-07-19 from git history, PLAN.md, and CLAUDE.md; each carries its original
decision date.

| # | Decision | Date |
|---|----------|------|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions as ADRs | 2026-07-19 |
| [0002](0002-crdt-lww-register-per-field.md) | CRDT with LWW-register per field as the core data model | 2026-04-28 |
| [0003](0003-hexagonal-architecture-archunit.md) | Hexagonal architecture, enforced by ArchUnit | 2026-04-28 |
| [0004](0004-contract-first-openapi.md) | Contract-first OpenAPI spec, code generation on both ends | 2026-04-28 |
| [0005](0005-backend-stack.md) | Backend stack: Java 21 + Spring Boot 3, PostgreSQL + JPA + Flyway | 2026-04-28 |
| [0006](0006-sse-for-realtime-sync.md) | Server-Sent Events (not WebSockets) for real-time sync | 2026-04-28 |
| [0007](0007-auth-google-sso-auth-code-jwt.md) | Auth: Google SSO, single-use auth-code exchange, stateless JWTs | 2026-04-28 |
| [0008](0008-frontend-stack.md) | Frontend stack: Vite + React 18 + TypeScript + Tailwind | 2026-07-12 |
| [0009](0009-docker-compose-nginx-deployment-unit.md) | One Docker Compose stack, dev to prod, behind an nginx edge proxy | 2026-07-12 |
| [0010](0010-prod-single-ec2-terraform-oidc-ssm.md) | Prod on a single EC2 via Terraform; CD with GitHub OIDC + SSM | 2026-07-12 |
| [0011](0011-observability-otel-collector-dash0.md) | Observability via an OTel Collector sidecar to Dash0, prod only | 2026-07-18 |
| [0012](0012-section-classification-dictionary-plus-learned-corrections.md) | Section classification via bundled dictionary + learned corrections | 2026-07-19 |
| [0013](0013-group-tenancy-invite-codes.md) | Group tenancy with single-use invite codes | 2026-07-20 |
