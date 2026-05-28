# Tasks

## TDD Ordering (Write test FIRST, watch it FAIL, then implement)

### Phase 1: Domain Layer (L0)
- [x] T0001: `User` entity — constructor, validation, defaults
- [x] T0002: `User` entity — edge cases (null email, blank name, no `@`)
- [x] T0003: `Merchant` entity — constructor, defaults
- [x] T0004: `Merchant` entity — edge cases
- [x] T0005: `Wallet` entity — constructor, defaults, `debit()`, `topUp()`
- [x] T0006: `Wallet` entity — edge cases (zero/negative amounts, insufficient funds)
- [x] T0007: `Payment` entity — constructor, defaults, `refund()`
- [x] T0008: `Payment` entity — edge cases (refund non-SUCCESS, version)
- [x] T0009: `PaymentSummary` value object

### Phase 2: Application Layer (L1)
- [x] T0010: `PaymentRepositoryPort` interface
- [x] T0011: `ProcessPaymentUseCase` — execute + edge cases
- [x] T0012: `WalletTransferUseCase` — execute (success, insufficient funds, missing wallet/merchant, pessimistic locking)
- [x] T0013: `TopUpWalletUseCase` — execute (success, zero/negative amounts)
- [x] T0014: `RefundPaymentUseCase` — execute (success, not found, not SUCCESS)
- [x] T0015: `GetPaymentUseCase` — execute
- [x] T0016: `ListUserPaymentsUseCase` — execute
- [x] T0017: `SearchPaymentsUseCase` — execute
- [x] T0018: `GetPaymentSummaryUseCase` — execute
- [x] T0019: `ProcessBatchPaymentsUseCase` — execute (within limit, exceeds 500)

### Phase 3: Infrastructure Layer (L2)
- [x] T0020: `UserJpaRepository` — H2 integration tests
- [x] T0021: `MerchantJpaRepository` — H2 integration tests
- [x] T0022: `WalletJpaRepository` — H2 integration tests, pessimistic lock query
- [x] T0023: `PaymentJpaRepository` — H2 integration tests, specification queries
- [x] T0024: `PaymentPersistenceAdapter` — implements `PaymentRepositoryPort`
- [x] T0025: `WalletTransferConcurrencyIntegrationTest`
- [x] T0026: `OtelConfigTest`

### Phase 4: Presentation Layer (L3)
- [x] T0027: `UserController` — POST, GET, GET by ID, DELETE
- [x] T0028: `MerchantController` — POST, GET, GET by ID, DELETE
- [x] T0029: `WalletController` — POST, GET, GET by ID, GET by userId
- [x] T0030: `PaymentController` — POST, wallet-transfer, topup, batch, refund, GET, list, reports, search
- [x] T0031: `GlobalExceptionHandler` — 400, 404, 409, 422, 500
- [x] T0032: Contract tests for all REST endpoints

### Phase 5: GraphQL Layer
- [x] T0033: `PaymentGraphQLController` — all GraphQL operations
- [x] T0034: `UserGraphQLController`, `WalletGraphQLController`, `MerchantGraphQLController` — entity GraphQL operations
- [x] T0035: GraphQL schema definition

### Phase 6: Observability
- [x] T0036: OpenTelemetry SDK configuration
- [x] T0037: `@WithSpan` annotations on all use cases
- [x] T0038: Structured logging with trace/span IDs
- [x] T0039: OTLP gRPC exporter configuration

### Phase 7: DevOps
- [x] T0040: Dockerfile (multi-stage build)
- [x] T0041: docker-compose.yaml (full stack)
- [x] T0042: Kubernetes manifests
- [x] T0043: Postman collection (41 requests, all scripted)
- [x] T0044: k6 load testing scripts

## Test Coverage Results

| Layer | Line Coverage | Branch Coverage | Tests |
|-------|--------------|-----------------|-------|
| Domain | 100% | 100% | 74 |
| Application | 100% | 100% | 36 |
| Infrastructure | 95%+ | 90%+ | 28 |
| Presentation | 95%+ | 90%+ | 14 |
| **Total** | — | — | **152** |
