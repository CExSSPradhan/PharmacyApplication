# Resume checkpoint — 29 September 2026

The user requested stopping development for today so they can test completed modules, then continuing tomorrow.

## Running now

- Frontend: http://localhost:5173 (Vite, port 5173).
- Backend: http://localhost:8080 (Spring Boot).
- PostgreSQL: isolated native cluster in `.runtime/pgdata`, port 55432.
- Login credentials are in `.env`. Do not copy secrets into tracked documentation.
- Java 21/Maven/Node 22 are installed only in `.tools/`; wrappers use them automatically.

Completed functional modules: authentication, medicines, suppliers, purchase entry, batch inventory and audit drill-down, FEFO sales and receipts, sale cancellation, dashboard, reports and team account creation. Owner and cashier roles are enforced on APIs. Local test results are in TESTING.md.

## Tomorrow

1. Review the user's local test feedback first.
2. Final backend regression passed: 18 tests. Start the latest backend if necessary. The running backend was started before the final metrics-token/readiness additions; core functional modules are the tested running version.
3. Finish the infrastructure verification: standalone Compose config validation passed; continue with Docker installation choice, Compose smoke test and PostgreSQL 18 Testcontainers. No Docker daemon is currently installed.
4. Exercise real Redis/RabbitMQ outbox delivery, outage recovery, DLQ and monitoring. Native profile currently disables Redis and RabbitMQ.
5. Review CI, OpenAPI, production override, script portability/cleanup and runbook before calling production packaging verified.
6. A local Git checkpoint was saved; review it before configuring a remote. No remote or push is configured. Files are organized by feature, formatted, with secrets/generated artifacts ignored.
7. Defer AWS/EC2 deployment until the user approves moving forward after local testing.

No Python dependency remains in the project. Temporary Python code was used only during development to extract the PDF and generate files. Normal runtime and setup use Java, React/TypeScript, shell and Node.
