#!/bin/bash
# One-time (rerunnable) setup of runtime secrets in SSM Parameter Store.
# Run locally with admin credentials after `terraform apply`:
#
#   deploy/aws/set-secrets.sh [path-to-env-file]   (default: ./.env)
#
# Reads GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET from the env file (the same
# OAuth client as local dev; register the prod redirect URI on it, see
# docs/aws-deploy.md). JWT_SECRET and DB_PASSWORD are generated fresh for prod
# — never reused from dev — and only if not already set (DB_PASSWORD must stay
# stable across deploys once postgres has initialized its volume).
#
# Kept out of Terraform on purpose: TF-managed parameter values land in the
# state file.
set -euo pipefail

REGION="${AWS_REGION:-eu-central-1}"
PREFIX="/shopmate/prod"
ENV_FILE="${1:-.env}"

[ -f "$ENV_FILE" ] || { echo "env file not found: $ENV_FILE" >&2; exit 1; }

env_value() {
  grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-
}

put_secret() { # name value overwrite(yes/no)
  local name="$PREFIX/$1" value="$2" overwrite="$3"
  if [ "$overwrite" = no ] &&
     aws ssm get-parameter --region "$REGION" --name "$name" > /dev/null 2>&1; then
    echo "keep   $name (already set)"
    return
  fi
  aws ssm put-parameter --region "$REGION" --name "$name" \
    --type SecureString --value "$value" --overwrite > /dev/null
  echo "wrote  $name"
}

GOOGLE_CLIENT_ID=$(env_value GOOGLE_CLIENT_ID)
GOOGLE_CLIENT_SECRET=$(env_value GOOGLE_CLIENT_SECRET)
[ -n "$GOOGLE_CLIENT_ID" ] && [ -n "$GOOGLE_CLIENT_SECRET" ] ||
  { echo "GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET missing in $ENV_FILE" >&2; exit 1; }

put_secret GOOGLE_CLIENT_ID "$GOOGLE_CLIENT_ID" yes
put_secret GOOGLE_CLIENT_SECRET "$GOOGLE_CLIENT_SECRET" yes
put_secret JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n')" no
put_secret DB_PASSWORD "$(openssl rand -hex 24)" no

echo "done — parameters under $PREFIX ($REGION)"
