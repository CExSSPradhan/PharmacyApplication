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

## Application configuration

Backend settings live in `backend/src/main/resources/application.properties`.
`application-prod.properties` is activated by `SPRING_PROFILES_ACTIVE=prod`; the production Compose overlay sets this automatically.
Copy `.env.production.example` to an untracked `.env` on the deployment host and populate every required value. Do not commit real credentials to properties files. Spring also supports external `config/application.properties` and `config/application-prod.properties`; restrict access to any files containing secrets.

Production startup rejects missing database credentials, short database or metrics secrets, demo seeding, and missing host/authentication settings for enabled Redis and RabbitMQ services. Owner email and password are required only when bootstrapping an empty users table. Use at least 12 password characters and at most 72 UTF-8 bytes; remove the bootstrap password from the deployment environment after the owner is created. Changing this value does not reset an existing owner's password.

Session lifetime (`SESSION_TTL`), cache lifetime (`CACHE_TTL`), per-minute login/API limits (`LOGIN_RATE_LIMIT`, `API_RATE_LIMIT`), and job intervals in milliseconds (`OUTBOX_PUBLISH_DELAY`, `MAINTENANCE_DELAY`) can be changed through environment variables or the corresponding `app.*` properties. Secrets belong only on the server; never expose them through frontend build variables.
## Free-tier cloud deployment

This setup runs the Vite frontend on Cloudflare Pages, the Spring Boot API on Render, and PostgreSQL on Supabase. Use the same Git branch for both builds. For an existing pharmacy database, restore the application data before starting Render. A fresh project runs Flyway and creates the first owner at startup.

1. Create a Free Supabase project in Singapore. In **Connect**, select the **Session pooler** on port `5432`. Set the Render values below using its host, username, and database password. Disable the Data API in the Supabase dashboard because the app uses JDBC. [Supabase Spring Boot guide](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot) · [Data API settings](https://supabase.com/docs/guides/api/securing-your-api#disable-the-data-api)
2. Create a private Supabase Storage bucket named `pharmacy-files` if you need to keep files in Supabase. The current app has no file upload or retrieval feature. Billing records live in PostgreSQL. [Supabase buckets](https://supabase.com/docs/guides/storage/buckets/fundamentals)
3. Choose a Cloudflare Pages project name and reserve its `pages.dev` hostname, or use the hostname of an existing project. On Render, create a Blueprint from this repository using `render.yaml`. Supply the environment variables below in Render. [Render Blueprints](https://render.com/docs/infrastructure-as-code)
4. In the Cloudflare Pages project, connect the Git repository. Set the root directory to `frontend`, build command to `npm run build`, and output directory to `dist`. Set `NODE_VERSION=22` and `VITE_API_ORIGIN=https://YOUR-SERVICE.onrender.com` for the production build. Use the actual HTTPS URL assigned by Render. If the assigned Pages hostname differs from the one entered on Render, update `FRONTEND_ORIGIN` on Render. [Cloudflare Pages build configuration](https://developers.cloudflare.com/pages/configuration/build-configuration/)
5. Check that `/actuator/health/readiness` on Render reports `UP`. From the Pages URL, sign in with the owner account and verify a read and a write using test data. Confirm the result persists after a reload.

| Render variable | Value |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://POOLER_HOST:5432/postgres?sslmode=require` |
| `DB_USER` | Session pooler username, such as `postgres.PROJECT_REF` |
| `DB_PASSWORD` | Supabase database password |
| `OWNER_EMAIL` | Owner email for a fresh database |
| `OWNER_PASSWORD` | Initial owner password, at least 12 characters and at most 72 UTF-8 bytes |
| `FRONTEND_ORIGIN` | Exact production Pages origin, such as `https://pharmacy-billing.pages.dev`, with no trailing slash |

Keep passwords in the Render dashboard. The Cloudflare build needs only the public API URL. This deployment reads directly from PostgreSQL and uses it for rate limiting. RabbitMQ-driven background notifications are disabled.

Render Free spins down after 15 minutes without traffic, and a cold start can take about a minute. Supabase Free includes a 500 MB database and 1 GB of file storage, can pause after low activity, and requires you to manage your own database backups. [Render Free limits](https://render.com/docs/free) · [Supabase pricing](https://supabase.com/pricing)
