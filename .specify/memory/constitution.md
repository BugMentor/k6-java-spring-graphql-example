# Project Constitution

## 1. Mission

Enterprise Payment Service — a production-grade, ACID-compliant payment platform that processes financial transactions with full LGTM observability.

## 2. Non-Negotiable Principles

### Data Integrity First
- All wallet balance mutations use pessimistic locking (`SELECT ... FOR UPDATE`)
- All payment state transitions use optimistic locking (`@Version`)
- Every transaction is atomic, consistent, isolated, and durable (ACID)

### Clean Architecture
- Domain layer has ZERO framework annotations (except JPA `@Entity` for Spring Data conventions)
- Dependencies point inward: Presentation → Application → Domain ← Infrastructure
- Port interfaces live in the application layer; adapters live in infrastructure

### Test-Driven Development
- L0: Domain unit tests (pure Java, no mocks needed)
- L1: Application use case tests (mocked ports)
- L2: Integration tests (real PostgreSQL)
- L3: API contract tests (REST Assured + Spring Boot)
- 100% line and branch coverage enforced on domain and application layers (JaCoCo)

### PostgreSQL Test Infrastructure — IRON LAW

- **PostgreSQL MUST be available for ALL tests (L0 through L3), always.**
- A real PostgreSQL 16 container is provisioned automatically via `docker run` before every test execution.
- No test shall require manual database setup. No `localhost:5432` hardcoded URLs. Port 55432.
- Docker MUST be available in all development, CI, and deployment environments. No exceptions.
- Every build (`mvn test`, `mvn verify`, CI pipeline) MUST run the full L0-L3 test suite against a real PostgreSQL container.
- No mock databases. No in-memory H2. Real PostgreSQL or the build fails.

### Pod Resource Limits — UNVIOLABLE
- **Memory:** 1 GiB per pod (requests and limits)
- **CPU:** 1 core per pod (1000m requests and limits)
- These limits are constitutional. No pod shall exceed 1 GiB RAM or 1 CPU core.
- The HPA shall enforce the escalation policy based on these hard limits.
- Memory request = limit = 1Gi. CPU request = limit = 1000m.

### Full LGTM Observability (Loki, Grafana, Tempo, Mimir)
- **Mimir** — Metrics storage (Prometheus-compatible, PromQL). No standalone Prometheus server.
- **Loki** — Log aggregation (LogQL)
- **Tempo** — Distributed tracing (OTLP)
- **Grafana** — Unified dashboards for all three signals
- OpenTelemetry Collector as the single ingestion and routing pipeline
- All metrics, traces, and logs flow through OTel Collector → LGTM backends
- Structured logging with `trace_id` and `span_id` in every log line

### Spec-Driven Development
- Every feature begins with a specification in `.specify/specs/`
- Specs are committed alongside code
- No code without a corresponding spec

## 3. Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| Database | PostgreSQL 16 (production), real PostgreSQL (tests) |
| Observability | OpenTelemetry Collector, Mimir (metrics), Loki (logs), Tempo (traces), Grafana (dashboards) |
| Testing | JUnit 5, Mockito, REST Assured, Spring Cloud Contract |
| Container | Docker, KinD (Kubernetes in Docker) |

## 4. Git Workflow

- `main` — production, protected
- `develop` — integration, protected
- `###-feature-name` — feature branches
- Squash-merge PRs only
- Never force-push to `main` or `develop`
- Tag releases: `v<major>.<minor>.<patch>`

### Deployment Replicas — UNVIOLABLE
- **Minimum pods:** 2 (ALWAYS, under any circumstance)
- **Maximum pods:** 30 (governed by HPA)
- HPA minReplicas is permanently locked at 2. Never scale to 1 or 0.

### HPA Scaling Thresholds — IRON LAW

These thresholds govern ALL horizontal pod autoscaling. They are constitutional and shall never be modified.

**UPSCALE** (either condition triggers):
- Mean CPU usage across all pods **greater than 80%**
- Mean RAM usage across all pods **greater than 60%**

**DOWNSCALE** (either condition triggers):
- Mean CPU usage across all pods **lower than 40%**
- Mean RAM usage across all pods **lower than 30%**

These are the ONLY valid thresholds. Any deviation is a constitutional violation.

| Direction | Condition | Threshold |
|-----------|-----------|-----------|
| UPSCALE | CPU | > 80% |
| UPSCALE | RAM | > 60% |
| DOWNSCALE | CPU | < 40% |
| DOWNSCALE | RAM | < 30% |
