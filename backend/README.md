# Backend — stock reservation service

Java 21 · Spring Boot 3 · Spring Kafka · PostgreSQL · Flyway

## Run

```bash
# Requires PostgreSQL on :5432 and Kafka on :9092
# (docker compose -f ../infra/docker-compose.yml up -d postgres kafka)
./gradlew bootRun
```

The service listens on `:8080`. Actuator health is at
`/actuator/health`, Prometheus metrics at `/actuator/prometheus`.

## Test

```bash
./gradlew test              # unit + integration (integration needs Docker for Testcontainers)
./gradlew test --tests '*Test'   # unit only
```

## Package structure

| Package          | Responsibility                                                   |
|------------------|-----------------------------------------------------------------|
| `domain`         | Entities, the stock invariant, domain exceptions — no framework |
| `reservation`    | Synchronous reservation use case + REST API                     |
| `stockmovement`  | Asynchronous Kafka consumer, retry topics, dead-letter handling |
| `inbox`          | Consumer-side idempotency (event de-duplication)                |
| `outbox`         | Transactional outbox and the relay that publishes domain events |
| `support`        | Persistence-exception classification, global error handling     |
| `config`         | Typed configuration, Kafka topic and observability setup        |

## Configuration

All tunables live under `inventory.*` in `application.yml` and bind to
`config.InventoryProperties`. The `docker` profile overrides host names for the
Compose network.
