# ADR-0010: Prod on a single EC2 via Terraform; CD with GitHub OIDC + SSM

Date: 2026-07-12 (decided) / 2026-07-18 (implemented) · Status: Accepted

## Context

Production hosting for a household-scale app should be cheap, reuse the
verified compose stack (ADR-0009), and support long-lived SSE connections.
ECS/Fargate costs more for always-on service and App Runner's request
timeouts kill SSE streams.

## Decision

- **One EC2 instance** (t4g.small arm64, ~15 €/mo) running the compose
  stack; Terraform for all infra (ECR, IAM, EC2, DNS, SSM), with **local
  gitignored state** — one operator, one machine.
- **CD without credentials or SSH:** GitHub Actions assumes an AWS role via
  **OIDC** (trust scoped to this repo's `main`), builds arm64 images to ECR,
  and triggers the on-instance deploy via **SSM Run Command**. Runtime
  secrets live in SSM Parameter Store, rendered to `.env` at deploy time.
- TLS via Let's Encrypt (certbot sidecar, webroot renewal).

## Consequences

- No AWS keys in GitHub, no SSH keys at all (Session Manager for shell
  access); secrets never in git or Terraform state.
- Single host = no HA, and postgres data dies with instance replacement —
  EBS snapshots (DLM) are a required follow-up before real household use.
- Local Terraform state is a single point of failure; `terraform import` is
  the recovery path.
