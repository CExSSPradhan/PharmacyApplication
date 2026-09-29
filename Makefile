.PHONY: dev setup backend frontend test build up down format
setup:
	node scripts/init-env.mjs
	./scripts/setup-local
	./scripts/npm ci
dev:
	./scripts/dev
backend:
	./scripts/start-backend
frontend:
	./scripts/npm run dev
test:
	./scripts/test-local
	./scripts/npm run lint
	./scripts/npm run test
build:
	./scripts/mvn package -DskipTests
	./scripts/npm run build
up:
	./scripts/compose up -d --build
down:
	./scripts/compose down
format:
	./scripts/format
