# Container and EC2 runbook

The application is prepared for containerization. This machine has no Docker daemon; container startup, image scanning and PostgreSQL 18 compatibility still need their CI/Docker verification. Native Java/React/PostgreSQL validation is recorded in TESTING.md. Do not treat an unexecuted workflow as a passed check.

## Local containers

Install Docker with Compose v2. Stop the native backend before using port 8080. From the repo root:

```sh
node scripts/init-env.mjs
./scripts/compose up -d --build --wait
# App: http://localhost:8088
./scripts/compose --profile monitoring up -d
# Grafana: http://localhost:3001; admin / GRAFANA_PASSWORD from .env
# RabbitMQ: http://localhost:15672; RABBITMQ_USER / RABBITMQ_PASSWORD
./scripts/compose logs -f backend
./scripts/compose down
```

`down` preserves named volumes. PostgreSQL 18 mounts `/var/lib/postgresql`, matching its versioned data-directory layout. Never use `down -v` for data you want to keep. Redis is disposable; PostgreSQL and RabbitMQ persist. Backend readiness does not depend on Redis/RabbitMQ availability.

## Deployment preparation

1. Run CI, including Testcontainers on PostgreSQL 18, Playwright against Compose, and image scans. Review pending advisories and replace image tags with approved immutable digests.
2. Create a staging environment. Provision an EC2 Linux host with Docker/Compose and adequate storage/memory, or use managed PostgreSQL. Measure before choosing instance sizes. Configure EBS encryption and backups.
3. Restrict security groups to HTTPS (443), HTTP (80 for redirect/certificates), and tightly controlled administration. Do not expose PostgreSQL, Redis, RabbitMQ management, Grafana or Prometheus publicly. Use SSM where available.
4. Supply production secrets through a managed secret store/deployment system. Create a separate production `.env` with `SEED_DEMO=false`, a new owner password, new database/cache/broker/metrics credentials, `SITE_DOMAIN`, and `TLS_EMAIL`. Do not copy local test credentials or the local database.
5. Split migration and application database roles before production. The convenient local Compose `POSTGRES_USER` is a superuser and is not a suitable least-privilege production application role. Grant the runtime role only required schema/table/sequence rights; execute Flyway under a separate migration role. The current application runs Flyway on startup; configure separate migration execution and disable runtime Flyway in that deployment.
6. Use versioned images from a passing CI commit. The manually invoked release workflow can publish GHCR images after a configured GitHub `release` environment approval. No workflow provisions AWS or deploys automatically.

## Staging or single-host TLS composition

Requires Docker Compose >= 2.24.4 for `!reset` port overrides. Point the domain to the host before starting Caddy.

```sh
docker compose --env-file .env \
  -f infra/docker-compose.yml -f infra/compose.production.yml \
  up -d --build --wait
```

Caddy terminates TLS and reverse-proxies the frontend. PostgreSQL/cache/broker remain on the internal network. For release images, replace the `image` values with your approved registry references, pull them, then use `up --no-build`. Keep the previous image digests for rollback. Back up before migrations. Run the purchase → sale → cancel journey on staging before exposing production traffic.

## Backup, restore and recovery

Native development:

```sh
./scripts/backup
./scripts/verify-restore
```

The verification script restores into a NEW timestamped database and compares medicine/sale/movement counts. It never replaces the running database. Freeze writes for a consistent cross-check during production restore drills.

Container backup example (protect the resulting file):

```sh
mkdir -p .runtime/backups
./scripts/compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d pharmacy -Fc' > .runtime/backups/pharmacy.dump
```

Schedule encrypted daily backups outside the host and test recovery. For production recovery-point requirements, enable PostgreSQL WAL archiving/PITR or managed backups. Restore into a new database first, validate migrations, row counts, stock balances and audit history, then switch the application during a maintenance window. Never overwrite the only working copy during a drill.

## Incident and rollback

1. Capture X-Request-ID, timestamps and relevant structured logs; do not collect auth tokens/passwords.
2. Check readiness, PostgreSQL connectivity, pool usage and outbox backlog. Redis/RabbitMQ outages should not roll back committed stock writes.
3. Stop writes if financial/stock consistency is in question. Reconcile batch balances against purchase/sale/cancellation movements.
4. Roll back to the previous image only if its schema contract is compatible. Do not edit applied Flyway migrations. Use a reviewed forward migration or restore-to-new-database plan otherwise.
5. Re-run health checks, authentication and a controlled transaction smoke test before resuming traffic.

Outstanding production work: trusted-proxy login limits, tighter DB roles, password lifecycle, dependency/image scans on CI, real Redis/broker outage tests, observability tuning, load measurements, monitoring alerts, image digest pinning, retention policies and off-host/PITR backup policy. AWS deployment is intentionally a later user-reviewed step.
