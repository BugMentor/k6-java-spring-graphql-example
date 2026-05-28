# Implementation Plan

## Architecture Decision

Chosen: **Clean Architecture + Spring Boot**  
Rationale: Separates concerns, enables independent testability of each layer, supports swapping infrastructure without touching business logic.

## Layer Breakdown

### Domain Layer (L0)
- `User`, `Merchant`, `Wallet`, `Payment`, `PaymentSummary`
- Pure Java. No framework annotations beyond `@Entity` for JPA.
- Business rules: email validation, debit/topUp methods, refund state machine.
- Tests: 100% line + branch coverage via pure unit tests.

### Application Layer (L1)
- 9 use cases: `CreateUserUseCase`, `ProcessPaymentUseCase`, `WalletTransferUseCase`, `TopUpWalletUseCase`, `RefundPaymentUseCase`, `GetPaymentUseCase`, `ListUserPaymentsUseCase`, `SearchPaymentsUseCase`, `GetPaymentSummaryUseCase`, `ProcessBatchPaymentsUseCase`
- 1 port interface: `PaymentRepositoryPort`
- All use cases annotated with `@WithSpan` for OpenTelemetry tracing.
- Tests: Use cases tested with mocked ports. 100% line + branch coverage.

### Infrastructure Layer (L2)
- JPA repositories: `UserJpaRepository`, `MerchantJpaRepository`, `WalletJpaRepository`, `PaymentJpaRepository`
- `PaymentPersistenceAdapter` implements `PaymentRepositoryPort` using Spring Data JPA.
- `PostgresPaymentRepository` — raw JDBC implementation of `PaymentRepositoryPort` (fallback).
- `OtelConfig` — OpenTelemetry SDK setup.
- `WalletJpaRepository.findByIdWithLock()` — pessimistic write lock query.
- Tests: H2 integration tests, Testcontainers PostgreSQL tests, concurrency tests.

### Presentation Layer (L3)
- REST controllers: `UserController`, `MerchantController`, `WalletController`, `PaymentController`
- GraphQL controllers: `PaymentGraphQLController`, `UserGraphQLController`, `WalletGraphQLController`, `MerchantGraphQLController`
- `GlobalExceptionHandler` — maps exceptions to HTTP status codes.
- Tests: Spring Cloud Contract (contract tests), REST Assured integration tests.

## Concurrency Strategy

| Operation | Strategy | Why |
|-----------|----------|-----|
| `debit()` | Pessimistic write lock | Prevents race conditions on wallet balance |
| `topUp()` | Pessimistic write lock | Prevents lost updates on wallet balance |
| `refund()` | Optimistic lock (`@Version`) | Low contention; idempotent operation |
| Payment save | Optimistic lock (`@Version`) | Standard JPA behavior |

## Observability Strategy

- OpenTelemetry Java Agent for automatic instrumentation
- `@WithSpan` on all use case `execute()` methods for manual instrumentation
- `@SpanAttribute` on key parameters (userId, amount, dates)
- OTLP gRPC exporter to OpenTelemetry Collector (port 4317)
- Logback pattern includes `trace_id` and `span_id` from MDC
- Spring Boot Actuator exposes health and metrics in OpenMetrics format (consumed by OTel Collector → Mimir)
- Metrics exposed via `/actuator/prometheus` endpoint (Spring Boot convention for OpenMetrics format)

## Database

- **Production:** PostgreSQL 16 via HikariCP connection pool (max 50, min 10 idle)
- **Test:** H2 in-memory database (fast, isolated)
- **Migrations:** Hibernate `ddl-auto: validate` (schema managed externally)
- **Batch insert:** JPA batch size of 50 with `order_inserts: true`

## Deployment

- Docker image built from `Dockerfile` (multi-stage)
- `docker-compose.yaml` — full stack (app + PostgreSQL + OTel Collector + Grafana + Loki + Tempo + Mimir)
- `k8s/` — Kubernetes manifests for production deployment
