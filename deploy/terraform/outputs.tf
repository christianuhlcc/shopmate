output "app_url" {
  value = "https://${local.fqdn}"
}

output "public_ip" {
  value = aws_eip.app.public_ip
}

output "instance_id" {
  value = aws_instance.app.id
}

output "ecr_registry" {
  value = local.ecr_registry
}

output "github_deploy_role_arn" {
  description = "Referenced (hardcoded) in .github/workflows/deploy.yml"
  value       = aws_iam_role.github_deploy.arn
}

output "google_oauth_redirect_uri" {
  description = "Register this in the Google Cloud console OAuth client"
  value       = "https://${local.fqdn}/login/oauth2/code/google"
}
