# Non-secret runtime config, read by deploy-on-instance.sh at deploy time.
# Secrets (JWT_SECRET, DB_PASSWORD, GOOGLE_*) are deliberately NOT managed by
# Terraform — they would end up in the state file. deploy/aws/set-secrets.sh
# writes them to the same ${local.ssm_prefix}/ path as SecureStrings.

resource "aws_ssm_parameter" "domain" {
  name  = "${local.ssm_prefix}/DOMAIN"
  type  = "String"
  value = local.fqdn
}

resource "aws_ssm_parameter" "ecr_registry" {
  name  = "${local.ssm_prefix}/ECR_REGISTRY"
  type  = "String"
  value = local.ecr_registry
}

resource "aws_ssm_parameter" "certbot_email" {
  name  = "${local.ssm_prefix}/CERTBOT_EMAIL"
  type  = "String"
  value = var.certbot_email
}

resource "aws_ssm_parameter" "dash0_endpoint" {
  name  = "${local.ssm_prefix}/DASH0_ENDPOINT"
  type  = "String"
  value = var.dash0_endpoint
}
