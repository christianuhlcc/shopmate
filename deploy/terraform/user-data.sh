#!/bin/bash
# First-boot setup (Terraform templatefile: ${repo_url}, ${compose_version}).
# Everything else — .env rendering, cert bootstrap, compose up — happens on
# every deploy via deploy/aws/deploy-on-instance.sh (SSM Run Command).
set -euxo pipefail

dnf install -y docker git
systemctl enable --now docker

# docker compose v2+ plugin (not packaged in AL2023 repos)
mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/${compose_version}/docker-compose-linux-$(uname -m)" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 1 GiB swap: headroom for JVM + postgres + nginx on a 2 GiB instance
dd if=/dev/zero of=/swapfile bs=1M count=1024
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Public repo: read-only clone, no deploy keys needed. Deploys fetch + reset.
git clone "${repo_url}" /opt/shopmate
