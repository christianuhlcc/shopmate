# AWS Deployment (Phase 10)

Production runs on a single EC2 instance (`t4g.small`, arm64, eu-central-1)
running the same compose stack as local dev, plus a production override for
ECR images and TLS. Decided 2026-07-12: cheapest option (~15 €/mo) that reuses
the verified `docker-compose.yml`; ECS/Fargate and App Runner were rejected
(cost / SSE timeout limits).

**Public URL:** https://shopmate.uhl-steine-scherben.org

## Moving parts

| Piece | Where |
|---|---|
| Infrastructure as code (ECR, IAM/OIDC, EC2, EIP, Route53, SSM config) | `deploy/terraform/` |
| Runtime secrets (JWT, DB password, Google OAuth) | SSM Parameter Store `/shopmate/prod/*`, written by `deploy/aws/set-secrets.sh` |
| CD workflow (build arm64 images → ECR → SSM Run Command) | `.github/workflows/deploy.yml` |
| On-instance deploy script (render `.env`, cert bootstrap, compose up) | `deploy/aws/deploy-on-instance.sh` |
| Production compose override (ECR images, TLS nginx, certbot renewal) | `docker-compose.prod.yml`, `nginx/nginx.prod.conf.template` |
| First-boot instance setup (docker, compose, swap, repo clone) | `deploy/terraform/user-data.sh` |

## How a deploy works

1. Push to `main` → CI runs (`ci.yml`).
2. CI success triggers the `Deploy` workflow (`workflow_run`); manual runs via
   `workflow_dispatch`. It assumes `shopmate-github-deploy` via **GitHub OIDC**
   — no AWS keys stored in GitHub; the role trusts only `main` of this repo.
3. Builds both images natively on an `ubuntu-24.04-arm` runner (the instance
   is arm64), tags `:<sha>` + `:latest`, pushes to ECR.
4. Sends an **SSM Run Command** to the instance (no SSH): git-reset
   `/opt/shopmate` to the deployed sha, then `deploy-on-instance.sh <sha>`,
   which renders `.env` from Parameter Store, logs into ECR, pulls, and
   `compose up -d`. The workflow polls the command and fails if it fails.

## TLS

- First deploy: `deploy-on-instance.sh` issues the Let's Encrypt cert with
  certbot **standalone** (nginx can't start its 443 server without a cert).
- After that: the `certbot` sidecar renews via **webroot** through nginx's
  ACME location every 12 h; nginx reloads itself every 6 h to pick up renewed
  certs.
- HTTP redirects to HTTPS; HSTS is set.

## Operations

```bash
# All infra changes: edit deploy/terraform/, then
cd deploy/terraform && terraform plan && terraform apply

# Rotate/refresh secrets (Google creds re-read from ./.env; JWT/DB kept unless absent)
deploy/aws/set-secrets.sh

# Manual deploy of current main
gh workflow run Deploy

# Shell on the instance (no SSH keys — SSM Session Manager)
aws ssm start-session --target i-0fda569fb7ada49b5 --region eu-central-1

# Logs on the instance
cd /opt/shopmate && docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
```

Terraform state is **local** (`deploy/terraform/*.tfstate`, gitignored) — one
operator, one machine. Don't lose it; `terraform import` is the fallback.
Secrets are never in state: Terraform manages only non-secret parameters
(`DOMAIN`, `ECR_REGISTRY`, `CERTBOT_EMAIL`).

## Caveats / follow-ups

- **Google sign-in in prod** needs the redirect URI
  `https://shopmate.uhl-steine-scherben.org/login/oauth2/code/google`
  registered on the OAuth client in the Google Cloud console (same client as
  local dev). Until then the app loads but sign-in fails with
  `redirect_uri_mismatch`.
- **Postgres data** lives in a docker volume on the instance's EBS root
  volume. It survives stop/start and redeploys, but **not**
  `terraform destroy` / instance replacement. Set up EBS snapshots (DLM)
  before real household use.
- `DB_PASSWORD` must stay stable once postgres initialized its volume
  (`set-secrets.sh` never overwrites it).
- The account ID / role ARN in `deploy.yml` are hardcoded on purpose (public
  repo — they're not secrets; OIDC trust is scoped to this repo's `main`).
