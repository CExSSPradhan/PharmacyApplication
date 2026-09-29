# Working on this repository

Use Java 21 and Node 22. Keep feature code in its feature package/folder. Controllers handle HTTP and validation; service methods own transactions and business rules. Do not expose JPA entities, store currency in floats, or mutate stock outside an audited transaction.

Use new Flyway migration files for schema changes. Never edit a migration already applied to a shared environment. Keep medicine/batch lock ordering deterministic and run the concurrency test for every stock change.

Before a PR:

```sh
./scripts/test-local
./scripts/npm run typecheck
./scripts/npm run lint
./scripts/npm run test
./scripts/npm run build
# When flows/UI change, start the app and run:
./scripts/npm run test:e2e
```

Use `make format` for Java/TypeScript/CSS formatting after frontend dependencies are installed. The project has `.editorconfig`, ESLint and Prettier configurations. API request examples and route changes belong in `docs/openapi.json`.

## Git

This is a standalone repository intended for a private remote you choose. No remote is configured or pushed automatically. Before pushing, inspect the staged files and confirm `.env`, `.runtime/`, `.tools/`, `node_modules/` and `target/` remain ignored.

```sh
git status
git remote add origin <your-repository-url>
git push -u origin main
```

Do not commit passwords, auth tokens, database dumps, real customer data or test screenshots containing sensitive information. Release images are built only when the release workflow is manually invoked. Configure protected branches and the `release` environment before using it.
