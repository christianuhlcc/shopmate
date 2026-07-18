resource "aws_security_group" "web" {
  name        = "shopmate-web"
  description = "HTTP/HTTPS from anywhere; no SSH (management via SSM)"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description      = "HTTP (ACME challenges + redirect to HTTPS)"
    from_port        = 80
    to_port          = 80
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description      = "HTTPS"
    from_port        = 443
    to_port          = 443
    protocol         = "tcp"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ssm_parameter.al2023_arm64.value
  instance_type          = var.instance_type
  subnet_id              = data.aws_subnets.default.ids[0]
  vpc_security_group_ids = [aws_security_group.web.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  user_data = templatefile("${path.module}/user-data.sh", {
    repo_url        = "https://github.com/${var.github_repo}.git"
    compose_version = var.compose_version
  })

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
  }

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }

  tags = {
    Name = "shopmate"
  }

  lifecycle {
    # The AMI parameter tracks the latest release; don't replace the instance
    # (and its postgres volume) every time Amazon publishes a new AMI.
    ignore_changes = [ami]
  }
}

# Stable public IP so the DNS record and Let's Encrypt cert survive stop/start.
resource "aws_eip" "app" {
  domain = "vpc"
  tags = {
    Name = "shopmate"
  }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}
