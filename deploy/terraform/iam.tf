# --- GitHub Actions deploy role (OIDC, no long-lived keys) ---------------------

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # AWS validates GitHub's cert against trusted CAs since 2023; thumbprints are
  # still required by the API but effectively ignored.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

data "aws_iam_policy_document" "github_trust" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # Only workflows on main of this repo may deploy. The second value covers
    # jobs that declare `environment: default` (e.g. to read Dash0 vars/secrets),
    # which GitHub issues with an environment-scoped sub claim instead of the
    # ref-based one.
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repo}:ref:refs/heads/main",
        "repo:${var.github_repo}:environment:default",
      ]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "shopmate-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_trust.json
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = [for r in aws_ecr_repository.app : r.arn]
  }

  statement {
    sid     = "DeployCommand"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
      aws_instance.app.arn,
    ]
  }

  statement {
    sid       = "DeployCommandStatus"
    actions   = ["ssm:GetCommandInvocation"]
    resources = ["*"]
  }

  # The workflow looks the instance up by its Name tag.
  statement {
    sid       = "FindInstance"
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# --- EC2 instance role ----------------------------------------------------------

data "aws_iam_policy_document" "ec2_trust" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "shopmate-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_trust.json
}

# SSM agent registration (Run Command target, Session Manager instead of SSH).
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ec2_app" {
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPull"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [for r in aws_ecr_repository.app : r.arn]
  }

  # Runtime secrets + config, rendered into .env by deploy-on-instance.sh.
  # SecureStrings use the AWS-managed aws/ssm key, so no extra kms:Decrypt needed.
  statement {
    sid     = "ReadRuntimeConfig"
    actions = ["ssm:GetParameter", "ssm:GetParameters", "ssm:GetParametersByPath"]
    resources = [
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}",
      "arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.ssm_prefix}/*",
    ]
  }
}

resource "aws_iam_role_policy" "ec2_app" {
  name   = "app"
  role   = aws_iam_role.ec2.id
  policy = data.aws_iam_policy_document.ec2_app.json
}

resource "aws_iam_instance_profile" "ec2" {
  name = "shopmate-ec2"
  role = aws_iam_role.ec2.name
}
