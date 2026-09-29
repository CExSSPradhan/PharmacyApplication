# Pharmacy Inventory System

A local-ready pharmacy workspace built with **Java 21 + Spring Boot 4.1.1**, **React 19.3 + TypeScript**, and **PostgreSQL**. It implements medicine/supplier maintenance, multi-line purchases, batch stock, FEFO billing, cancellation, dashboard, reports and stock audit history from the supplied implementation playbook.

## Start on this computer

The application lives in `~/Downloads/pharmacy-system`. Java 21, Maven 3.9.11 and Node 22 were downloaded into the ignored `.tools/` directory; the scripts use them automatically. PostgreSQL 16.13 runs in an isolated project data directory on port 55432. No Python runtime is needed.

```sh
cd ~/Downloads/pharmacy-system
./scripts/dev
```

Open **http://localhost:5173**. Sign in using `OWNER_EMAIL` and `OWNER_PASSWORD` from `.env`. The initial email is `owner@pharmacy.local`; the password was generated locally and is not committed. `scripts/dev` keeps the app in the foreground; Ctrl+C stops the app processes. PostgreSQL remains running so your data is preserved. If the app is already running from setup, open the URL directly rather than launching a second copy.

For separate development terminals:

```sh
./scripts/start-backend
# In another terminal:
./scripts/npm run dev
```

Backend: `http://localhost:8080`. Health: `http://localhost:8080/actuator/health`.

## Fresh checkout

Install Java 21, Maven 3.9+, Node 22, and either Docker Compose v2 or PostgreSQL 16+. Run:

```sh
node scripts/init-env.mjs
./scripts/npm ci
# Native PostgreSQL on this Mac:
./scripts/setup-local
./scripts/dev
# Or a complete container stack:
./scripts/compose up -d --build --wait
```

For another native PostgreSQL installation, set `PG_BIN` to its `bin` directory. The native setup script is designed for a dedicated `pharmacy` role and databases; external databases can instead be configured through DB_URL/DB_USER/DB_PASSWORD and started with `scripts/start-backend`.

Docker serves the app at **http://localhost:8088** and uses PostgreSQL 18, Redis 8, RabbitMQ and optional Prometheus/Grafana. Native and container setups use separate databases. Docker is not installed on the current machine, so container smoke checks remain pending; use CI or install Docker before testing them.

## What to try

1. Log in as owner. Add/edit a medicine and a supplier.
2. Record a purchase with several medicines, batch numbers, expiry dates and unit prices. A missing medicine can be created inline.
3. Open Inventory. Check current, low, out-of-stock, near-expiry and expired tabs. Open a medicine’s batches, then its movement history.
4. Create a sale. The backend allocates the earliest-expiring eligible batches and calculates the receipt from their prices.
5. Open the receipt and cancel with a reason. The exact batch quantities are restored once and the cancelled receipt stays in history.
6. Review Reports and Dashboard. Add a CASHIER from Team to test permissions.

Demo data uses synthetic medicine names/stock only. Browser tests add records prefixed `E2E`; these are test fixtures, not real business transactions. Medicine pricing is illustrative.

## Repository structure

```text
pharmacy-system/
├── backend/
│   ├── pom.xml, Dockerfile
│   └── src/
│       ├── main/java/com/example/pharmacy/
│       │   ├── auth/, user/, config/, common/
│       │   ├── catalog/, medicine/, supplier/
│       │   ├── purchase/, sale/, stock/, inventory/
│       │   └── dashboard/, reporting/, notification/
│       ├── main/resources/db/migration/
│       └── test/java/com/example/pharmacy/
├── frontend/
│   ├── src/api/, src/components/
│   ├── src/features/{auth,catalog,dashboard,inventory,transactions,reports,users}/
│   ├── e2e/
│   └── Dockerfile, nginx.conf, package-lock.json
├── infra/                  # Compose, Caddy, Prometheus, Grafana
├── scripts/                # Local startup, checks, backup/restore
├── docs/                   # Architecture, API, testing, deployment
├── .github/workflows/      # CI and manually invoked release images
├── .env.example            # Names only; real values stay private
└── Makefile
```

Generated tools, database files, credentials, dependencies, logs, screenshots, test reports and build output are ignored by Git. Keep `.env` private. No Git remote or AWS deployment is configured.

## Checks

```sh
./scripts/test-local            # Integration/concurrency/API tests on pharmacy_test
./scripts/npm run typecheck
./scripts/npm run lint
./scripts/npm run test
./scripts/npm run build
./scripts/npm run test:e2e       # Running local app + Chrome required
./scripts/verify-restore        # New restore database; never overwrites live data
```

`mvn -f backend/pom.xml verify` uses Testcontainers PostgreSQL 18 by default. `scripts/test-local` explicitly selects the isolated `pharmacy_test` database; tests truncate only that test database. E2E tests use the running app and leave clearly named test records. CI runs E2E against an ephemeral Compose stack.

## Documentation

- [Architecture and business decisions](docs/ARCHITECTURE.md)
- [Test results and acceptance coverage](docs/TESTING.md)
- [OpenAPI contract](docs/openapi.json) — import into Postman or Swagger Editor
- [Docker / EC2 staging and recovery runbook](docs/DEPLOYMENT.md)
- [Contribution and Git workflow](CONTRIBUTING.md)

The implementation is ready for local functional testing once the checks in TESTING.md pass. Production rollout requires the remaining Docker/staging/security/operational checks listed in the runbook.
