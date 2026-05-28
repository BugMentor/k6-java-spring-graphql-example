# Payment Service — Enterprise Payment Platform

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" alt="Spring Boot 3.2">
  <img src="https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 16">
  <img src="https://img.shields.io/badge/Kubernetes-KinD-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white" alt="Kubernetes">
  <img src="https://img.shields.io/badge/OpenTelemetry-Instrumented-000000?style=for-the-badge&logo=opentelemetry&logoColor=white" alt="OpenTelemetry">
  <img src="https://img.shields.io/badge/Grafana-LGTM-F46800?style=for-the-badge&logo=grafana&logoColor=white" alt="Grafana LGTM">
</p>

Java 21 + Spring Boot 3.2 ACID-compliant payment platform with REST and GraphQL endpoints. Full LGTM observability (Loki, Grafana, Tempo, Mimir), Clean Architecture, TDD, and OpenTelemetry instrumentation. Auto-scales on Kubernetes via HPA.

---

## Table of Contents

- [What is HPA?](#what-is-hpa)
- [Architecture](#architecture)
- [Observability (LGTM Stack)](#observability-lgtm-stack)
- [Quick Start](#quick-start)
- [API Endpoints](#api-endpoints)
- [Load Testing (k6)](#load-testing-k6)
- [Grafana Dashboard](#grafana-dashboard)
- [Testing Pyramid](#testing-piperamid)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)

---

## What is HPA?

**HPA (Horizontal Pod Autoscaler)** is a Kubernetes controller that automatically scales the number of pod replicas based on observed CPU and memory utilization.

```mermaid
flowchart TD
    HPA["HPA<br/>minReplicas: 2<br/>maxReplicas: 30"]
    Metrics["CPU > 80% / RAM > 60%<br/>CPU < 40% / RAM < 30%"]
    HPA -->|monitors| Metrics
    Metrics -->|triggers| S2

    subgraph S2["2 pods (min)"]
        direction LR
        P1["pod"] --- P2["pod"]
    end

    S2 -->|scale up| S4

    subgraph S4["4 pods"]
        direction LR
        Q1["pod"] --- Q2["pod"] --- Q3["pod"] --- Q4["pod"]
    end

    S4 -->|scale up| S8

    subgraph S8["8 pods"]
        direction LR
        R1["pod"] --- R2["pod"] --- R3["pod"] --- R4["pod"] --- R5["pod"] --- R6["pod"] --- R7["pod"] --- R8["pod"]
    end

    S8 -->|scale up| SMAX["… up to 30 pods"]
```

### Constitutional HPA Rules (Unviolable)

| Rule | Value |
|------|-------|
| Minimum pods | **2** (ALWAYS, under any circumstance) |
| Maximum pods | 30 (governed by HPA) |
| Memory per pod | 1 GiB (requests = limits) |
| CPU per pod | 1 core (1000m, requests = limits) |

HPA `minReplicas` is permanently locked at 2. Never scale to 1 or 0. Every pod gets exactly 1GiB RAM and 1 CPU core — no exceptions.

### HPA Scaling Thresholds — IRON LAW

```
UPSCALE:   CPU > 80%  OR  RAM > 60%
DOWNSCALE: CPU < 40%  OR  RAM < 30%
```

| Direction | Metric | Threshold |
|-----------|--------|-----------|
| UPSCALE | CPU | > 80% |
| UPSCALE | RAM | > 60% |
| DOWNSCALE | CPU | < 40% |
| DOWNSCALE | RAM | < 30% |

These thresholds are constitutional. Never modify them. Every k6 test must start with exactly 2 pods.

### Upscaling & Downscaling Behavior

**Scale Up (when CPU > 80% or RAM > 60%):**

| Policy | Value | Period | Effect |
|--------|-------|--------|--------|
| Percent | 100% | every 15s | Double the pod count (e.g. 2 → 4 → 8 → 16) |
| Pods | +4 per step | every 15s | Add 4 pods per step (e.g. 2 → 6 → 10) |
| **Winner** | **Max** | — | The more aggressive policy wins |

Stabilization window: 30 seconds before first scale-up (avoids false triggers).

**Scale Down (when CPU < 40% or RAM < 30%):**

| Policy | Value | Period | Effect |
|--------|-------|--------|--------|
| Percent | 50% | every 60s | Halve the pod count gradually |
| Pods | -2 per step | every 60s | Remove 2 pods per step |
| **Winner** | **Min** | — | The more conservative policy wins |

Stabilization window: 300 seconds (5 minutes) before first scale-down (prevents rapid scale-down/scale-up cycles).

**Example scale-up sequence under heavy load:**

```mermaid
graph LR
    A["2 pods"] -->|"15s, +100%"| B["4 pods"]
    B -->|"15s, +100%"| C["8 pods"]
    C -->|"15s, +100%"| D["16 pods"]
    D -->|"15s, +4"| E["20 pods"]
    E -->|"15s, +4"| F["24 pods"]
    F -->|"15s, +4"| G["28 pods"]
    G -->|"15s, +4"| H["30 pods (hard limit)"]
```

### What is OOM?

**OOM (Out Of Memory)** occurs when a pod's memory consumption exceeds its 1GiB limit. Kubernetes kills the pod with `OOMKilled` status. The HPA detects this via the RAM upscale threshold (> 60%) and scales up to distribute load, but if demand exceeds capacity faster than new pods can start, OOM kills cascade. You can monitor OOM in Grafana under the **"OOM & Cluster Resources"** row.

---

## Architecture

```mermaid
graph TD
    subgraph Presentation["PRESENTATION LAYER"]
        REST["REST Controllers (/v1/)"]
        GQL["GraphQL API (/graphql)"]
    end
    subgraph Application["APPLICATION LAYER"]
        UC["9 Use Cases (@WithSpan)"]
        Port["PaymentRepositoryPort"]
    end
    subgraph Domain["DOMAIN LAYER"]
        Ent["User / Merchant / Wallet<br/>Payment / PaymentSummary"]
    end
    subgraph Infrastructure["INFRASTRUCTURE LAYER"]
        JPA["JPA Repositories"]
        HikariCP["HikariCP"]
        OTel["OTel Collector"]
        Mimir["Mimir"]
    end
    Presentation -->|depends on| Application
    Application -->|depends on| Domain
    Infrastructure -.->|implements| Application
```

- **Domain** — Zero framework annotations. Pure Java entities and business rules.
- **Application** — Use cases orchestrate domain logic. Depends ONLY on domain.
- **Infrastructure** — JPA repositories, HikariCP connection pool (max 50), external adapters.
- **Presentation** — REST controllers and GraphQL API endpoints.

---

## Observability (LGTM Stack)

```mermaid
graph LR
    PS["Payment Service"]
    OTel["OTel Collector"]
    Mimir["Mimir (metrics, PromQL)"]
    Loki["Loki (logs, LogQL)"]
    Tempo["Tempo (traces)"]
    Grafana["Grafana<br/>Unified Dashboard<br/>CPU, RAM, OOM, latency, errors"]

    PS --> OTel
    OTel --> Mimir
    OTel --> Loki
    OTel --> Tempo
    Mimir --> Grafana
    Loki --> Grafana
    Tempo --> Grafana
```

No standalone Prometheus — Mimir is the metrics backend (PromQL-compatible). All three signals (metrics, logs, traces) flow through the OpenTelemetry Collector as a single ingestion pipeline.

### Metrics Sources

| Source | What it provides |
|--------|-----------------|
| `/actuator/prometheus` (Spring Boot) | JVM heap, threads, GC, HTTP latency, HikariCP pool |
| kubelet `/metrics/cadvisor` (per node) | Container memory, CPU, OOM failures, machine-level stats |

Both are scraped by Prometheus and remote-written to Mimir. Grafana queries Mimir with PromQL.

---

## Quick Start

```bash
# 1. Build the application
mvn clean package -DskipTests

# 2. Build Docker image
docker build -t payment-service:1.0.0 .

# 3. Load image into KinD
kind load docker-image payment-service:1.0.0 --name payment-fight

# 4. Deploy to Kubernetes
kubectl apply -f k8s/payment-service.yaml

# 5. Verify 2 pods running (constitutional minimum)
kubectl get pods -n payments -l app=payment-service

# 6. Port-forward for local access
kubectl port-forward -n payments service/payment-nodeport 30080:80

# 7. Run a load test
k6 run -e BASE_URL=http://localhost:30080 benchmark/k6/payment-service-load-test.js

# 8. Grafana
# http://localhost:3000 (admin/admin)
```

---

## API Endpoints

### REST (`/v1/`)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST/GET/DELETE | `/users` | User CRUD |
| POST/GET/DELETE | `/merchants` | Merchant CRUD |
| POST/GET/DELETE | `/wallets` | Wallet CRUD |
| POST | `/payments` | Create payment |
| POST | `/payments/batch` | Batch create payments |
| POST | `/payments/wallet-transfer` | Transfer between wallet and merchant |
| POST | `/payments/wallets/{id}/topup` | Add funds to wallet |
| PUT | `/payments/{id}/refund` | Refund a payment |
| GET | `/payments/{id}` | Get payment by ID |
| GET | `/payments/user/{userId}` | List user's payments |
| GET | `/payments/search` | Search payments with filters |
| GET | `/payments/reports/summary` | Aggregate payment summary |

### GraphQL (`/graphql`)

| Operation | Type | Description |
|-----------|------|-------------|
| `processPayment` | Mutation | Process a payment via GraphQL |
| `walletTransfer` | Mutation | Transfer between wallet and merchant |
| `topUpWallet` | Mutation | Add funds to wallet |
| `refundPayment` | Mutation | Refund a payment |
| `payment` | Query | Retrieve payment by ID |
| `payments` | Query | List user payments |
| `searchPayments` | Query | Search with criteria |
| `paymentSummary` | Query | Aggregated report |
| `user` / `users` | Query | User queries |
| `wallet` / `wallets` | Query | Wallet queries |
| `merchant` / `merchants` | Query | Merchant queries |

GraphiQL UI available at `GET /graphql` (browser).

---

## Load Testing (k6)

All tests live in `benchmark/k6/`. Purpose-annotated with HPA/OOM behavior expectations.

| File | VUs | Duration | Purpose |
|------|-----|----------|---------|
| `max-capacity-test.js` | 5,000 | 1h (15min ramp) | Breaking point discovery. Pushes 2→30 pods to failure. |
| `payment-service-load-test.js` | 50 (configurable) | 5min | Baseline benchmark. All endpoints, realistic traffic mix. |
| `cpu-stress-test.js` | 100 (configurable) | ~25min | CPU HPA trigger. Batch ops, GraphQL, transfers, reports. |
| `memory-stress-test.js` | 100 (configurable) | ~11min | Memory HPA + OOM trigger. Large payloads, sustained hold. |

### Shared utility: `_shared.js`
Provides `setupTestEntities` (creates user, merchant, wallet, funds it), `teardownTestEntities`, `buildRampStages`, and `printScalingBox`.

### Running Tests

```bash
# Individual test
k6 run -e BASE_URL=http://localhost:30080 benchmark/k6/max-capacity-test.js

# Via benchmark script
./benchmark/run-benchmark.sh all      # Full suite
./benchmark/run-benchmark.sh cpu      # CPU stress only
./benchmark/run-benchmark.sh mem      # Memory stress only
./benchmark/run-benchmark.sh tp       # Max capacity only
./benchmark/run-benchmark.sh load     # Load test (VUS=200 DURATION=10m)
```

Results are saved to `benchmark/k6/results/`.

---

## Grafana Dashboard

Available at `http://localhost:3000` → **"Payment Service - Real-Time Monitoring"**

### Dashboard Rows

| Row | Panels | What it shows |
|-----|--------|---------------|
| Auto-Scaling (HPA) | Pod count, Mean CPU %, Mean Heap % | HPA scaling status and thresholds |
| Resource Consumption | CPU, Heap, Active requests, RPS by endpoint | Real-time resource utilization |
| OOM & Cluster Resources | OOM kills, Cluster CPU %, Cluster RAM % | Out-of-memory events and node-level metrics |
| Latency | RPS, Avg/P50/P90/P95/P99/Max ms | Response time percentiles per endpoint |
| Errors & Reliability | 5xx rate, Status code distribution, DB pool | Error rates and connection pool health |
| JVM & Database | Heap, GC pause, Threads, DB pool, Memory pools | JVM internals per pod |
| Throughput | Total RPS, Avg ms per endpoint, RPS/core | Aggregate throughput metrics |

### Data Sources

| Source | Backend | Protocol |
|--------|---------|----------|
| Mimir | Metrics (PromQL) | `/api/v1/push` via Prometheus remote-write |
| Loki | Logs | Via OTel Collector |
| Tempo | Traces | Via OTel Collector |

---

## Testing Piperamid

| Level | Pattern | Count | Type |
|-------|---------|-------|------|
| L0 | Domain unit tests | 7 | Pure Java, no mocks. Entities, validation, business rules. |
| L1 | Application use case tests | 9 | Mocked ports. Use case orchestration. |
| L2 | Integration tests | 6 | Real PostgreSQL (no H2, no Testcontainers). |
| L3 | API contract tests | 4 | REST Assured + Spring Boot. Full endpoint contracts. |

100% line and branch coverage enforced on domain and application layers via JaCoCo.

---

## Kubernetes Deployment

Deployed on **KinD (Kubernetes in Docker)** cluster `payment-fight` (3 nodes: 1 control-plane, 2 workers).

### Cluster Topology

```mermaid
graph TD
    subgraph payments["payments namespace"]
        subgraph appPods["Payment Service Pods"]
            Pod1["payment-svc (pod 1)<br/>1Gi/1CPU"]
            Pod2["payment-svc (pod 2)<br/>1Gi/1CPU"]
        end
        PG["postgres (pod)<br/>4Gi/2CPU"]
        HPA["HPA<br/>2 min, 30 max<br/>UP: CPU>80% / RAM>60%<br/>DOWN: CPU<40% / RAM<30%"]
        Prom["prometheus (scraper)"]
        OTelCol["otel-collector (pipeline)"]
        MimirS["mimir (metrics)"]

        HPA -.->|manages| Pod1
        HPA -.->|manages| Pod2
        Pod1 --> PG
        Pod2 --> PG
        Prom --> MimirS
        OTelCol --> MimirS
    end
```

### Deployment Manifests

| File | Purpose |
|------|---------|
| `k8s/payment-service.yaml` | Deployment (2 replicas), Service (ClusterIP), Secret |
| `k8s/otel-collector.yaml` | OTel Collector deployment + config (metrics/traces/logs routing) |
| `benchmark/k8s/hpa.yaml` | HPA definition (UPS: CPU>80%/RAM>60%, DOWN: CPU<40%/RAM<30%, min 2, max 30) |
| `benchmark/k8s/infrastructure.yaml` | Namespaces, PostgreSQL, PVC, Services |
| `benchmark/k8s/payment-service-deployment.yaml` | Full deployment with probes, env, resources |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Build | Maven |
| Database | PostgreSQL 16 (HikariCP, max 50 connections, min idle 10) |
| Observability | OpenTelemetry Collector, Prometheus (scraper), Mimir (metrics), Loki (logs), Tempo (traces), Grafana (dashboards) |
| Testing | JUnit 5, Mockito, REST Assured |
| Load Testing | k6 (up to 5,000 VUs tested) |
| Container | Docker, KinD (Kubernetes in Docker) |
| Concurrency | Pessimistic locks (`@Lock(PESSIMISTIC_WRITE)`) for wallet mutations, Optimistic locks (`@Version`) for payment state transitions |
| JVM | ZGC, String Deduplication, AlwaysPreTouch, MaxRAMPercentage=75% |

---

## Project Structure

```
.
├── .specify/                  # Spec Kit artifacts (constitution, specs, templates)
│   ├── memory/
│   │   └── constitution.md    # Governing principles (2-pod minimum, resource limits)
│   └── specs/
├── src/
│   ├── main/java/             # Application source (domain, application, infra, presentation)
│   └── test/java/             # L0-L3 tests (real PostgreSQL)
├── benchmark/
│   ├── k6/                    # 4 load test scripts + shared utilities
│   │   └── results/           # Test output (JSON, CSV, logs)
│   ├── k8s/                   # Kubernetes manifests (HPA, deployment, infrastructure)
│   ├── grafana/
│   │   ├── dashboards/        # Dashboard JSON (Payment Service - Real-Time Monitoring)
│   │   └── lgtm-datasources.yaml
│   ├── lgtm/                  # Mimir config
│   ├── docker-compose.yaml    # Standalone Grafana for local dashboard viewing
│   └── run-benchmark.sh       # Test suite runner
├── k8s/                       # Production Kubernetes manifests
│   ├── payment-service.yaml   # Deployment + Service + Secret
│   ├── otel-collector.yaml    # OTel Collector config
│   └── lgtm-stack.md          # LGTM Helm install guide
├── postman/                   # API collection (41 requests)
├── AGENTS.md                  # AI agent instructions (constitutional rules enforced)
├── GEMINI.md                  # Gemini CLI context
├── docker-compose.yaml        # Full LGTM stack + PostgreSQL + Payment Service
└── Dockerfile                 # Multi-stage Docker build
```
