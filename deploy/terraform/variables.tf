variable "region" {
  description = "AWS region for all resources"
  type        = string
  default     = "eu-central-1"
}

variable "zone_name" {
  description = "Route53 hosted zone the app subdomain lives in (must already exist)"
  type        = string
  default     = "uhl-steine-scherben.org"
}

variable "subdomain" {
  description = "Subdomain for the app (fqdn = subdomain.zone_name)"
  type        = string
  default     = "shopmate"
}

variable "github_repo" {
  description = "GitHub repository (owner/name) allowed to assume the deploy role via OIDC"
  type        = string
  default     = "christianuhlcc/shopmate"
}

variable "instance_type" {
  description = "EC2 instance type. arm64 (t4g.*) — the deploy workflow builds arm64 images"
  type        = string
  default     = "t4g.small"
}

variable "certbot_email" {
  description = "Email for Let's Encrypt registration / expiry notices"
  type        = string
  default     = "uhl.christian@googlemail.com"
}

variable "dash0_endpoint" {
  description = "Dash0 OTLP-over-HTTP ingress endpoint for the org's region (see https://app.dash0.com/settings/endpoints)"
  type        = string
  # Org lives in GCP europe-west4 (verified 2026-07-18: POST /v1/traces → 200)
  default     = "https://ingress.europe-west4.gcp.dash0.com"
}

variable "compose_version" {
  description = "docker compose plugin release installed on the instance at first boot"
  type        = string
  default     = "v5.3.1"
}

locals {
  fqdn         = "${var.subdomain}.${var.zone_name}"
  ecr_registry = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
  ssm_prefix   = "/shopmate/prod"
}
