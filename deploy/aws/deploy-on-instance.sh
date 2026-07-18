#!/bin/bash
# Runs ON the EC2 instance as root, invoked by the deploy workflow via SSM Run
# Command (which git-resets /opt/shopmate to the deployed commit first).
#
#   deploy-on-instance.sh <image-tag>
#
# Renders .env from SSM Parameter Store, logs into ECR, bootstraps the Let's
# Encrypt cert on first run, then pulls + restarts the compose stack.
set -euo pipefail

IMAGE_TAG="${1:?usage: deploy-on-instance.sh <image-tag>}"
cd /opt/shopmate

# Region via IMDSv2 (the instance profile provides credentials).
IMDS_TOKEN=$(curl -sS -X PUT "http://169.254.169.254/latest/api/token" \
  -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
AWS_DEFAULT_REGION=$(curl -sS -H "X-aws-ec2-metadata-token: $IMDS_TOKEN" \
  "http://169.254.169.254/latest/meta-data/placement/region")
export AWS_DEFAULT_REGION

get_param() {
  aws ssm get-parameter --name "/shopmate/prod/$1" --with-decryption \
    --query Parameter.Value --output text
}

DOMAIN=$(get_param DOMAIN)
ECR_REGISTRY=$(get_param ECR_REGISTRY)
CERTBOT_EMAIL=$(get_param CERTBOT_EMAIL)

umask 077
cat > .env <<EOF
COMPOSE_PROJECT_NAME=shopmate
DOMAIN=$DOMAIN
ECR_REGISTRY=$ECR_REGISTRY
IMAGE_TAG=$IMAGE_TAG
DB_PASSWORD=$(get_param DB_PASSWORD)
GOOGLE_CLIENT_ID=$(get_param GOOGLE_CLIENT_ID)
GOOGLE_CLIENT_SECRET=$(get_param GOOGLE_CLIENT_SECRET)
JWT_SECRET=$(get_param JWT_SECRET)
EOF

aws ecr get-login-password | docker login --username AWS --password-stdin "$ECR_REGISTRY"

compose() {
  docker compose -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

# First-run cert bootstrap: nginx can't start its 443 server without a cert,
# so issue one standalone (needs :80 free) before bringing the stack up.
# Renewals afterwards are handled by the certbot sidecar via webroot.
if ! docker run --rm -v shopmate_letsencrypt:/etc/letsencrypt alpine \
    test -e "/etc/letsencrypt/live/$DOMAIN/fullchain.pem"; then
  echo "No certificate for $DOMAIN yet — running certbot standalone"
  compose stop nginx 2>/dev/null || true
  docker run --rm -p 80:80 -v shopmate_letsencrypt:/etc/letsencrypt certbot/certbot \
    certonly --standalone -d "$DOMAIN" -m "$CERTBOT_EMAIL" \
    --agree-tos --no-eff-email --non-interactive
fi

compose pull backend frontend
compose up -d --remove-orphans

echo "Waiting for backend health"
status=starting
for _ in $(seq 1 60); do
  cid=$(compose ps -q backend)
  status=$(docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo starting)
  [ "$status" = healthy ] && break
  sleep 5
done
if [ "$status" != healthy ]; then
  echo "Backend failed to become healthy (status: $status)"
  compose logs --tail 100 backend
  exit 1
fi

# Smoke-check TLS via localhost — an EC2 instance can't reach its own EIP
# (no hairpin NAT). --resolve keeps SNI/Host = $DOMAIN so the cert validates.
ok=false
for _ in $(seq 1 12); do
  if curl -fsS -o /dev/null --resolve "$DOMAIN:443:127.0.0.1" "https://$DOMAIN/"; then
    ok=true
    break
  fi
  sleep 5
done
if [ "$ok" != true ]; then
  echo "HTTPS smoke check failed"
  compose logs --tail 50 nginx
  exit 1
fi
echo "Deployed $IMAGE_TAG — https://$DOMAIN is up"

docker image prune -f > /dev/null
