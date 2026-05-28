# Technical Research Decisions

## 1. Dual Locking Strategy

**Decision:** Pessimistic locking for wallets, optimistic locking for payments.  
**Rationale:** Wallet balance is a shared mutable resource with high contention — pessimistic locks prevent race conditions. Payment state transitions are low-contention — optimistic locks are simpler and more performant.  
**Alternatives considered:**
- Pessimistic everywhere: Too slow, unnecessary for payments
- Optimistic everywhere: Would cause retry overhead on wallet operations
- Event sourcing: Overkill for this scale; introduces eventual consistency complexity

## 2. PostgreSQL over H2 for Production

**Decision:** PostgreSQL 16 for production, H2 for tests.  
**Rationale:** PostgreSQL provides `SELECT ... FOR UPDATE` for pessimistic locking, proven ACID compliance, and rich query capabilities. H2 in-memory is fast and isolated for tests.  
**Alternatives considered:**
- MySQL: Lacks some PostgreSQL-specific features (SKIP LOCKED, etc.)
- H2 everywhere: Not robust enough for production workloads
- Testcontainers for all tests: Too slow; H2 is sufficient for most integration tests

## 3. JPA + Raw JDBC Hybrid

**Decision:** Primary persistence via Spring Data JPA with `PaymentPersistenceAdapter`. Raw JDBC fallback via `PostgresPaymentRepository`.  
**Rationale:** JPA provides rapid development with Spring Data conventions. The raw JDBC implementation demonstrates Clean Architecture port/adapter pattern — the `PaymentRepositoryPort` interface can be swapped.  
**Alternatives considered:**
- Pure JPA: Good, but less demonstration of port/adapter pattern
- Pure JDBC: No ORM convenience, more boilerplate
- R2DBC: Reactive is unnecessary for this scale

## 4. OpenTelemetry Agent + Manual Instrumentation

**Decision:** OTel Java Agent for auto-instrumentation + `@WithSpan` annotations on use cases.  
**Rationale:** Agent provides zero-code instrumentation for HTTP, JDBC, JPA. Manual `@WithSpan` annotations capture business-level spans at the use case boundary.  
**Alternatives considered:**
- Micrometer Tracing: Less vendor-neutral, OTel is the industry standard
- Manual SDK only: Missing HTTP/JDBC spans
- Agent only: Missing business-context spans

## 5. GraphQL + REST Dual API

**Decision:** Both GraphQL and REST endpoints.  
**Rationale:** GraphQL provides flexible queries with dynamic field selection for enterprise clients. REST is simpler for modern clients and internal tooling. Both call the same use cases.  
**Alternatives considered:**
- REST only: Wouldn't support enterprise GraphQL clients needing flexible data queries
- GraphQL only: Too complex for simple CRUD and tooling

## 6. Spring Boot 3.2 + Java 17

**Decision:** Spring Boot 3.2.5, Java 17 (LTS).  
**Rationale:** Spring Boot 3.2 is the latest stable series with virtual threads support. Java 17 is the current LTS with long support window. Java 21 (next LTS) is targeted for future upgrade.  
**Alternatives considered:**
- Java 21: Good but less tested in enterprise; upgrade path is clear
- Quarkus/Micronaut: Faster startup, but smaller ecosystem for SOAP and enterprise patterns

## 7. Maven over Gradle

**Decision:** Maven with `spring-boot-starter-parent`.  
**Rationale:** Maven is the enterprise standard, predictable builds, strong plugin ecosystem (JaCoCo, Spring Cloud Contract, docker-compose).  
**Alternatives considered:** Gradle is more flexible but less standardized in enterprise Java shops.
