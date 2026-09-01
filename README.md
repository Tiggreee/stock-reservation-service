# Stock Reservation Service

An event-driven inventory reservation system built around one invariant:

> For every SKU: `reserved >= 0`, `on_hand >= 0`, and `reserved <= on_hand`.
> Available stock (`on_hand - reserved`) can never go negative.

It holds that invariant under thousands of concurrent reservation requests and
through database failures, broker retries, and dropped network calls.

| Layer | Stack |
|-------|-------|
| Backend | Java 21 · Spring Boot 3 · Spring Kafka · PostgreSQL · Flyway |
| Frontend | React 19 · TypeScript (strict) · Vite · TanStack Query v5 |
| Infra | Docker Compose — PostgreSQL, Kafka (KRaft), Kafka UI, Prometheus, Grafana |

## Design in one picture

```
 React console ──POST /reservations (201 / 409)──►  REST adapter ─┐
                                                                  ▼
 Warehouse (WMS) ──wms.stock-movements.v1──► Kafka ──► Stock consumer ──► Reservation core ──► PostgreSQL
                    (retry topics + .dlt)                                     │  invariant     stock_level
                                                                             │  optimistic    reservation
                                                                             │  lock, ledger  stock_ledger
                                                                             ▼                outbox / inbox
                                                          outbox relay ──► inventory.events.v1
```

- **Synchronous** path for customer reservations — an immediate `201` or
  `409 INSUFFICIENT_STOCK`, guarded by optimistic locking with bounded
  in-transaction retry.
- **Asynchronous** path for the warehouse feed — a Kafka consumer with
  non-blocking retry topics (exponential backoff), an explicit dead-letter
  topic, an idempotent inbox, and a failure classifier that separates transient
  errors (retry) from permanent ones (dead-letter immediately).
- **Transactional outbox** so a domain event is published if and only if its
  transaction committed.

## What's implemented

| Area | Detail |
|------|--------|
| Concurrency | Optimistic `@Version` locking + bounded retry; a 200-caller race test asserts zero oversell |
| Event consumer | `@RetryableTopic` non-blocking retry (≈10s → 50s → 4m) → `.dlt`; queryable dead-letter store with one-call redrive |
| DB-failure handling | `PersistenceExceptionClassifier` → `CONTENTION` / `TRANSIENT` / `PERMANENT`; unknown failures fail closed |
| Idempotency | Client `Idempotency-Key` (unique constraint) · consumer inbox de-dup · transactional outbox |
| Correctness backstop | `CHECK (reserved >= 0 AND on_hand >= 0 AND reserved <= on_hand)` at the database |
| Observability | `/actuator/prometheus` — `inventory_oversell_total` (must be 0), reservation rate by outcome, movement processing p99, outbox lag, dead-letter depth; provisioned Grafana dashboard |
| Frontend | Optimistic reserve/release with snapshot + rollback; failure classification; reconcile on settled |
| Tests | 36 backend (JUnit + Testcontainers: Postgres + Kafka) · 4 frontend (Vitest + MSW) |
| CI | GitHub Actions — backend build+test, frontend lint/types/test/build, container image build |

## Run it

```bash
# 1. Infrastructure
docker compose -f infra/docker-compose.yml up -d postgres kafka kafka-ui prometheus grafana

# 2. Backend (Java 21 required)
cd backend && ./gradlew bootRun          # http://localhost:8080

# 3. Frontend
cd web && npm install && npm run dev      # http://localhost:5173
```

Or run the whole thing, backend included, in containers:

```bash
docker compose -f infra/docker-compose.yml up --build
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080/api/v1 |
| Actuator / Prometheus scrape | http://localhost:8080/actuator |
| Kafka UI | http://localhost:8085 |
| Prometheus | http://localhost:9090 |
| Grafana (anonymous admin) | http://localhost:3001 |

## Demo

```bash
API=http://localhost:8080/api/v1

# open a SKU with 8 units, reserve 3
curl -s -XPOST $API/stock -H 'Content-Type: application/json' -d '{"sku":"DEMO-1","onHand":8}'
curl -s -XPOST $API/reservations -H 'Content-Type: application/json' \
  -d '{"sku":"DEMO-1","quantity":3,"idempotencyKey":"demo-key-1"}'

# over-reserve -> 409 INSUFFICIENT_STOCK
curl -s -XPOST $API/reservations -H 'Content-Type: application/json' \
  -d '{"sku":"DEMO-1","quantity":99,"idempotencyKey":"demo-key-2"}'

# a warehouse goods receipt over Kafka
echo 'DEMO-1|{"eventId":"r-1","sku":"DEMO-1","type":"RECEIPT","quantity":25,"occurredAt":"2026-01-01T00:00:00Z"}' \
  | docker exec -i stock-reservation-kafka-1 /opt/kafka/bin/kafka-console-producer.sh \
      --bootstrap-server localhost:9092 --topic wms.stock-movements.v1 \
      --property parse.key=true --property key.separator='|'

curl -s $API/stock/DEMO-1        # on_hand is now 33
curl -s $API/dead-letters        # empty
```

## Documentation

- [`backend/README.md`](backend/README.md) — module layout, configuration
- [`web/README.md`](web/README.md) — frontend commands and structure
- [`docs/frontend/optimistic-updates.md`](docs/frontend/optimistic-updates.md) — snapshot handling and the rollback decision table

## Scope

Single-warehouse; single Kafka broker locally (production notes call for 3+,
`min.insync.replicas=2`, `acks=all`); the warehouse feed is driven by a
`kafka-console-producer` one-liner rather than a real WMS; auth is out of scope.

## License

MIT — see [LICENSE](LICENSE).
