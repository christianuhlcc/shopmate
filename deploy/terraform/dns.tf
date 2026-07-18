resource "aws_route53_record" "app" {
  zone_id = data.aws_route53_zone.app.zone_id
  name    = local.fqdn
  type    = "A"
  ttl     = 300
  records = [aws_eip.app.public_ip]
}
