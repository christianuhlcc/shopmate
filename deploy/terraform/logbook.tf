# Logbook: a second, private site on this instance (personal training dashboards from a separate, private repo).
# It rides on shopmate's nginx (include + mounts in docker-compose.prod.yml) and its own systemd job on the host;
# this file only adds the AWS side: DNS name, a private bucket (code/data transfer, nightly DB backup), the
# instance's access to both, and the non-secret config. Secrets (/garmin/prod/GARMIN_API_KEY, /garmin/prod/HTPASSWD)
# are written outside Terraform, like shopmate's, so they never land in the state file.

locals {
  logbook_fqdn       = "logbook.${var.zone_name}"
  logbook_ssm_prefix = "/garmin/prod"
}

resource "aws_route53_record" "logbook" {
  zone_id = data.aws_route53_zone.app.zone_id
  name    = local.logbook_fqdn
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}

# --- bucket: deploy/ (code + one-time data bundle), backup/ (garmin.db.gz + FIT files)

resource "aws_s3_bucket" "logbook" {
  bucket = "logbook-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_public_access_block" "logbook" {
  bucket                  = aws_s3_bucket.logbook.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_ownership_controls" "logbook" {
  bucket = aws_s3_bucket.logbook.id
  rule {
    object_ownership = "BucketOwnerEnforced" # no ACLs at all
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "logbook" {
  bucket = aws_s3_bucket.logbook.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Every run overwrites backup/garmin.db.gz; versioning keeps the history (and protects against a bad run).
resource "aws_s3_bucket_versioning" "logbook" {
  bucket = aws_s3_bucket.logbook.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "logbook" {
  bucket     = aws_s3_bucket.logbook.id
  depends_on = [aws_s3_bucket_versioning.logbook]

  rule {
    id     = "expire-old-versions"
    status = "Enabled"
    filter {}
    noncurrent_version_expiration {
      noncurrent_days           = 30
      newer_noncurrent_versions = 10
    }
    abort_incomplete_multipart_upload {
      days_after_initiation = 2
    }
  }

  rule {
    id     = "expire-deploy-bundles"
    status = "Enabled"
    filter {
      prefix = "deploy/"
    }
    expiration {
      days = 7
    }
  }
}

resource "aws_s3_bucket_policy" "logbook_tls_only" {
  bucket     = aws_s3_bucket.logbook.id
  depends_on = [aws_s3_bucket_public_access_block.logbook]
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyInsecureTransport"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.logbook.arn, "${aws_s3_bucket.logbook.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })
}

# --- non-secret config for deploy/server/setup.sh and the Mac scripts

resource "aws_ssm_parameter" "logbook_domain" {
  name  = "${local.logbook_ssm_prefix}/DOMAIN"
  type  = "String"
  value = local.logbook_fqdn
}

resource "aws_ssm_parameter" "logbook_bucket" {
  name  = "${local.logbook_ssm_prefix}/BUCKET"
  type  = "String"
  value = aws_s3_bucket.logbook.bucket
}

# --- instance access: read its parameters, read deploy bundles, write backups

data "aws_iam_policy_document" "ec2_logbook" {
  statement {
    sid     = "ReadLogbookConfig"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.logbook_ssm_prefix}",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.logbook_ssm_prefix}/*",
    ]
  }

  statement {
    sid       = "ListBucket"
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.logbook.arn]
  }

  statement {
    sid       = "DeployBundles"
    actions   = ["s3:GetObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.logbook.arn}/deploy/*"]
  }

  # Put + Get (aws s3 sync compares); no Delete: a compromised host can't erase the backup history.
  statement {
    sid       = "Backups"
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${aws_s3_bucket.logbook.arn}/backup/*"]
  }
}

resource "aws_iam_role_policy" "ec2_logbook" {
  name   = "logbook"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_logbook.json
}

output "logbook_url" {
  value = "https://${local.logbook_fqdn}"
}
