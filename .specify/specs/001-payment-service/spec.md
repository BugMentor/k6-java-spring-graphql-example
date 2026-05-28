# 001 — Payment Service Core

**Status:** Implemented  
**Date:** 2024-04-30  
**TDD Level:** L0 → L1 → L2 → L3 complete

## Overview

Enterprise Payment Service is an ACID-compliant financial transaction platform. It manages Users, Merchants, Wallets, and Payments with dual-locking concurrency control, full OpenTelemetry observability, and both REST and GraphQL APIs.

## User Stories

### US-01: User Management
**As a** platform operator  
**I want to** create, read, and delete users  
**So that** I can manage customer accounts

### US-02: Merchant Management
**As a** platform operator  
**I want to** register merchants with unique API keys  
**So that** payments can be attributed to the correct merchant

### US-03: Wallet Management
**As a** customer  
**I want to** have a wallet linked to my account  
**So that** I can store funds and make payments

### US-04: Wallet Top-Up
**As a** customer  
**I want to** add funds to my wallet  
**So that** I have sufficient balance for transactions

### US-05: Wallet Transfer
**As a** customer  
**I want to** transfer funds from my wallet to a merchant  
**So that** I can pay for goods/services

### US-06: Direct Payment
**As a** platform operator  
**I want to** create payments without wallet involvement  
**So that** external payment methods can be recorded

### US-07: Batch Payments
**As a** platform operator  
**I want to** process up to 500 payments in a single request  
**So that** high-volume payment processing is efficient

### US-08: Payment Refund
**As a** customer  
**I want to** refund a successful payment  
**So that** erroneous transactions can be reversed

### US-09: Payment Search & Reports
**As a** finance analyst  
**I want to** search payments by amount, type, status and view summary reports by date range  
**So that** I can reconcile financial data

## Architecture

```
Presentation (REST / GraphQL)
    │
    ▼
Application (Use Cases)
    │
    ├── Ports (interfaces)
    │       │
    ▼       ▼
Domain (Entities, Value Objects)    Infrastructure (JPA, JDBC, OTel)
```

- **Domain:** `User`, `Merchant`, `Wallet`, `Payment`, `PaymentSummary` — pure business logic
- **Application:** 9 use cases orchestrating business operations via ports
- **Infrastructure:** JPA repositories, JDBC fallback (`PostgresPaymentRepository`), OpenTelemetry config
- **Presentation:** REST controllers (`/v1/`), GraphQL API (`/graphql`)

## Data Model

See [data-model.md](./data-model.md)

## API Contracts

See [contracts/](./contracts/)

## Concurrency

| Operation | Lock Strategy | Mechanism |
|-----------|--------------|-----------|
| Wallet balance mutation | Pessimistic | `@Lock(PESSIMISTIC_WRITE)` via `findByIdWithLock()` |
| Payment state transition | Optimistic | `@Version` field on `Payment` entity |
| Payment update (refund) | Optimistic | `@Version` field checked by JPA on `save()` |

## Error Handling

All errors returned as JSON with structure:
```json
{
  "timestamp": "ISO-8601",
  "status": 400,
  "error": "Bad Request",
  "message": "Human-readable description",
  "details": "Exception-specific detail"
}
```

| HTTP Status | Exception | Scenario |
|-------------|-----------|----------|
| 400 | `IllegalArgumentException` | Validation failure, not found dependencies, insufficient funds, batch overflow |
| 404 | Controller-level `null` check | Entity not found |
| 409 | `ObjectOptimisticLockingFailureException` | Concurrent update conflict |
| 422 | `IllegalStateException` | Business rule violation (e.g., refund non-SUCCESS payment) |
| 500 | `RuntimeException` | Unhandled server errors |

## Observability

- All use case `execute()` methods annotated with `@WithSpan`
- Structured logs include `trace_id` and `span_id` via MDC
- OTLP gRPC export to OpenTelemetry Collector
- Actuator exposes all endpoints including health, metrics in OpenMetrics format (consumed by Mimir via OTel Collector)

## Testing Results

- 152 tests, 0 failures
- 100% line + branch coverage on domain and application layers
- L0: Domain unit tests (entity validation, business methods, edge cases)
- L1: Use case tests with mocked ports
- L2: H2 integration tests for all JPA repositories
- L3: Spring Cloud Contract tests for REST endpoints
