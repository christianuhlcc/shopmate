# Plan: move the shopmate EC2 instance to Spot (January 2027)

Written 2026-11-26 after a cost audit. Status: **planned, not done.** Do it after the t4g free trial ends.

## Why

The instance (`i-0fda569fb7ada49b5`, t4g.small, eu-central-1b) hosts shopmate and the logbook
(logbook.uhl-steine-scherben.org). Two users, so a few minutes of downtime now and then are fine.

- Until **2026-12-31** the t4g.small runs free (t4g free trial, 750 h/month). From 2027-01-01 the on-demand price
  applies: $0.0192/h, about **$14/month**.
- The box is oversized for CPU but not for RAM (measured 2026-11-26, 30 days):
  - CPU averages 1–2% and peaks at 3% most days. The credit balance stays at the 576 cap, with no surplus credits.
  - RAM: 639 of 1,846 MB used, 1 GB available, 36 MB swap, no OOM kills, memory pressure 0. The backend JVM takes
    about 310 MB (capped with `-XX:MaxRAMPercentage=50` in `docker-compose.prod.yml`) and Postgres about 40 MB.
- Spot t4g.small in eu-central-1b cost $0.0071–0.0084/h over the 90 days to 2026-11-26, about **$6/month**. That's
  about the same as an on-demand t4g.micro, but keeps the 2 GB of RAM.

| Option | $/month (excl. VAT) |
|---|---|
| t4g.small on-demand (default from Jan) | 14.02 |
| t4g.micro on-demand (1 GB, tight) | ~7.00 |
| t4g.micro 1-year Savings Plan | ~4.50 |
| **t4g.small Spot** (chosen) | **~6.00** |
| t4g.micro Spot | ~3.50 |

The whole account would go from about $24.50 to about $13 per normal month including VAT. The rest is public IPv4 at
$3.72, EBS 20 GB at $1.90 and one Route 53 zone at $0.50. The domain is $16/year, billed in November.

## How Spot behaves here

- Use a **persistent** spot request with interruption behaviour **stop**. On interruption AWS gives 2 minutes' warning,
  then stops the instance (a clean shutdown). It restarts the same instance when capacity is back. The instance ID, root
  EBS volume (Postgres data, `/srv/garmin`, `garmin.db`) and Elastic IP all stay.
- Downtime is usually minutes. If there's no capacity it can be longer, and nothing pages anyone. The garmin-sync timer
  catches up on the next run, and the iPhone/Mac uploads just fail until then.
- There's no max price, so it's capped at the on-demand price and the bill can never be worse than today's.
- The free trial does **not** cover Spot, so switching before January only costs money.

## The catch: Terraform will replace the instance

You can't turn a running on-demand instance into a Spot instance. Adding `instance_market_options` to
`aws_instance.app` (in `deploy/terraform/ec2.tf`) makes Terraform **destroy and recreate** it. Two consequences:

1. **The root disk would be lost.** Because of `ignore_changes = [ami]`, a replacement boots the AMI from the config,
   which is the *latest* AL2023 image, as a blank machine. So first make an AMI of the current instance and point `ami`
   at it.
2. **`user-data.sh` would run again** on the new instance (cloud-init runs per-instance scripts for each new instance
   ID). It isn't idempotent: `git clone` into the existing `/opt/shopmate` fails, it overwrites the active `/swapfile`,
   and it adds a second fstab line. Add a guard first.

## Steps

Work on a branch in this repo, commit, then apply. Deploy only committed code.

0. **Check first**
   - It is 2027-01-01 or later, and the free-tier page in the Billing console shows the t4g trial has ended.
   - Spot price is still about $0.008/h:
     `aws ec2 describe-spot-price-history --region eu-central-1 --instance-types t4g.small --product-descriptions Linux/UNIX --availability-zone eu-central-1b --max-items 5`
   - Check the interruption rate for t4g.small in eu-central-1 in the Spot Instance Advisor.
   - `terraform plan` on main shows no changes (no drift).

1. **Make user-data idempotent** (`deploy/terraform/user-data.sh`). Right after `set -euxo pipefail`:
   ```bash
   # Booted from an image of an already provisioned instance (e.g. the Spot migration): nothing to do.
   if [ -d /opt/shopmate/.git ]; then echo "already provisioned"; exit 0; fi
   ```

2. **Freeze and back up** (on the instance via `aws ssm start-session --target i-0fda569fb7ada49b5`):
   ```bash
   sudo systemctl start garmin-sync          # one last run; it ends with the garmin.db backup to S3
   sudo systemctl stop garmin-sync.timer garmin-inbox.path
   sudo docker exec shopmate-postgres-1 pg_dumpall -U postgres | gzip > /tmp/shopmate-pre-spot.sql.gz
   ```
   Copy the dump off the box, e.g. to the logbook bucket under `backup/`. The instance can write there.

3. **Create the AMI** (this reboots the instance so the disk is consistent; about 5 min down):
   ```bash
   AMI=$(aws ec2 create-image --region eu-central-1 --instance-id i-0fda569fb7ada49b5 \
         --name "shopmate-pre-spot-$(date +%Y%m%d)" --query ImageId --output text)
   aws ec2 wait image-available --region eu-central-1 --image-ids "$AMI"; echo "$AMI"
   ```
   After the reboot the timer and path units come back enabled. Stop them again if the apply isn't straight after.

4. **Terraform change** (`deploy/terraform/ec2.tf`, resource `aws_instance.app`):
   ```hcl
   ami = "ami-…"   # the image from step 3 (was data.aws_ssm_parameter.al2023_arm64.value)

   instance_market_options {
     market_type = "spot"
     spot_options {
       spot_instance_type             = "persistent"
       instance_interruption_behavior = "stop"
     }
   }

   credit_specification {
     cpu_credits = "standard"   # no surplus-credit charges; CPU use is ~2%
   }
   ```
   You can make the AMI a variable instead of a literal. Keep `ignore_changes = [ami]`.

5. **Plan and check it carefully.** Expected: `aws_instance.app` **must be replaced**, `aws_eip_association.app`
   updated or replaced. Nothing else should be destroyed: the EIP, DNS records, buckets and IAM must stay. If anything
   else shows up, stop.

6. **Apply.** About 5–10 min down: the old instance is destroyed, then the new one starts from the AMI and the EIP is
   re-associated.

7. **Verify**
   - `aws ec2 describe-instances --filters Name=tag:Name,Values=shopmate --query 'Reservations[].Instances[].[InstanceId,InstanceLifecycle,State.Name,PublicIpAddress]'`
     → `spot`, `running`, `63.183.107.155`.
   - The shopmate app works and https://logbook.uhl-steine-scherben.org loads.
   - On the box: `sudo docker ps` shows 5 containers, `swapon --show` shows a single 1 GB swap,
     `grep -c swapfile /etc/fstab` = 1, `systemctl status garmin-sync.timer garmin-inbox.path` are active,
     `sudo systemctl start garmin-sync` succeeds.
   - On the Mac, `deploy/mac/push.py` (garmin repo) still works.

8. **Update the instance ID** (it changes once): garmin repo `AGENTS.md` (server log section), this repo
   `docs/aws-deploy.md`, shopmate-nginx `docs/aws-deploy.md`, and the Claude memory `logbook-hosting.md`.
   `deploy/deploy.sh` (garmin) finds the instance by tag and needs no change.

9. **Clean up after a week without problems:** deregister the AMI and delete its snapshot (about $0.05/GB-month for
   the ~9 GB used), and delete the pg dump.

## Rollback

Remove `instance_market_options` and apply again. That replaces it back to on-demand, still from the AMI, so keep the
AMI until step 9. If the new instance is broken, set `ami` to the AMI from step 3 again and apply; the data is in the
image as of step 3.

## Optional

- An EventBridge rule on `EC2 Spot Instance Interruption Warning` sending an email, to know when it was down.
- An AWS Budgets alert for EC2 alone (the account budget `gesamtbudget` is $30 and trips each November when the domain
  renews).
