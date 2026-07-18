# One ECR repository per image; the deploy workflow pushes :<git-sha> and :latest.

resource "aws_ecr_repository" "app" {
  for_each = toset(["backend", "frontend"])

  name         = "shopmate/${each.key}"
  force_delete = true # personal project: allow `terraform destroy` with images present

  image_scanning_configuration {
    scan_on_push = true
  }
}

# Keep the last 10 images per repo so the registry doesn't grow unbounded.
resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep last 10 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
