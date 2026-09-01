# Stock Reservation Service

An event-driven inventory reservation system. It holds one invariant — you can
never reserve stock that isn't there — under high concurrency and through
database and broker failures.

- **Backend** — Java 21, Spring Boot 3, Spring Kafka, PostgreSQL, Flyway
- **Frontend** — React 19, TypeScript, Vite, TanStack Query
- **Infra** — Docker Compose (PostgreSQL, Kafka, Kafka UI, Prometheus, Grafana)

## Layout

| Path        | Contents                                                        |
|-------------|----------------------------------------------------------------|
| `backend/`  | Spring Boot service — REST API, Kafka consumer, domain model   |
| `web/`      | React + TypeScript single-page app                             |
| `infra/`    | Docker Compose stack and observability configuration           |

See [`backend/README.md`](backend/README.md) and [`web/README.md`](web/README.md)
for module-specific instructions.

## Quick start

```bash
# 1. Infrastructure
docker compose -f infra/docker-compose.yml up -d

# 2. Backend
cd backend && ./gradlew bootRun

# 3. Frontend
cd web && npm install && npm run dev
```

## License

MIT — see [LICENSE](LICENSE).
