# Local test checkpoint — 29 September 2026

## Passed

- Java backend compilation and JAR packaging. Final regression: 18 tests passed (15 integration/API tests plus 3 cache-fallback tests).
- Standalone Docker Compose configuration validation passed; no daemon/container execution performed.
- 15 PostgreSQL integration/API tests: three-line purchase, second-line rollback, duplicate supplier invoice, batch metadata mismatch, FEFO 5+3 allocation, exact BigDecimal totals, expired exclusion, expiry-today boundary, multi-line sale rollback, concurrent last-unit sale, cancellation restoration, concurrent duplicate cancellation, inactive medicine, inventory categories, committed outbox writes, authentication/logout, validation, pagination and cashier restrictions.
- Frontend TypeScript/build, ESLint, and two React Testing Library login tests.
- Two Playwright tests in local Chrome: purchase → inventory increase → sale → cancellation → exact stock restoration with audit movements; desktop dashboard and mobile navigation.
- npm dependency audit: zero known vulnerabilities at the checkpoint.
- Backup restored into a separate timestamped database; medicine/sale/stock-movement counts matched.

The tests used real native PostgreSQL 16.13. Default Maven tests are configured for Testcontainers PostgreSQL 18.1 when Docker is available.

## Remaining verification

- Docker daemon is not installed. Full Compose startup, PostgreSQL 18 Testcontainers, RabbitMQ delivery/DLQ behavior, real Redis/broker outage recovery, monitoring dashboards and image scans have not been executed here.
- CI workflows and release-image workflow are written but have not run on GitHub.
- Final regression after metrics-token authorization, database readiness and cache-fallback changes passed. The running server still uses the previous functional build; these infrastructure-only changes take effect on restart.
- Load tests, production least-privilege DB roles, trusted-proxy rate limits and off-host backup policy remain before deployment.

## Commands and artifacts

```sh
./scripts/test-local
./scripts/npm run typecheck
./scripts/npm run lint
./scripts/npm run test
./scripts/npm run build
./scripts/npm run test:e2e
./scripts/verify-restore
```

Backend reports: `backend/target/surefire-reports/`. Browser report: `frontend/playwright-report/`. Local logs, screenshots and backup files: `.runtime/`. All are excluded from Git. Browser tests leave synthetic records named `E2E medicine ...`; avoid interpreting these as real stock.
