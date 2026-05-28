# AGENTS.md

## Spec Kit — Source of Truth

> **GITHUB SPEC KIT (https://github.com/github/spec-kit) IS THE SINGLE SOURCE OF TRUTH FOR ALL DEVELOPMENT METHODOLOGY IN THIS PROJECT.**

This project strictly follows **Spec-Driven Development (SDD)** as defined by GitHub Spec Kit. All features, bug fixes, and refactorings MUST flow through the Spec Kit workflow. No code shall be written without a corresponding specification.

---

## Methodology Stack

| Principle | Meaning |
| :--- | :--- |
| **SDD (Spec-Driven Development)** | Specifications are executable. Write specs first, then implement. Specifications live in `.specify/specs/` and are committed alongside code. |
| **TDD (Test-Driven Development)** | L0 → L1 → L2 → L3 testing pyramid. Write the test FIRST, watch it FAIL, then implement. |
| **KISS (Keep It Simple, Stupid)** | Prefer simplicity. No over-engineering. One class, one responsibility. |
| **DRY (Don't Repeat Yourself)** | Extract duplication into shared abstractions. Single source of truth per concept. |
| **Clean Architecture** | Domain layer has ZERO external dependencies. Presentation → Application → Domain ← Infrastructure. Dependencies point inward. |
| **Strict Gitflow** | `main` is production. `develop` is integration. Feature branches: `###-feature-name`. Never commit directly to `main` or `develop`. |

---

## Testing Pyramid (TDD Levels)

```
L3: Contract Tests (Spring Cloud Contract)     ← API boundaries
L2: Integration Tests (H2 / Testcontainers)    ← DB, messaging, external systems
L1: Use Case Tests (mocked ports)              ← Application logic
L0: Domain Tests (pure unit tests)             ← Core business rules
```

- Each test level must pass before moving to the next.
- Domain and Application layers require **100% line and branch coverage** (enforced by JaCoCo).
- Write tests FIRST. Commit tests and implementation together.

---

## Spec Kit Workflow

### Core Commands

| Command | Purpose |
| :--- | :--- |
| `/speckit.constitution` | Establish or update project governing principles |
| `/speckit.specify` | Define WHAT to build (requirements, user stories) |
| `/speckit.clarify` | Clarify underspecified areas (run BEFORE plan) |
| `/speckit.plan` | Create technical implementation plan |
| `/speckit.tasks` | Generate actionable task breakdown |
| `/speckit.analyze` | Cross-artifact consistency analysis |
| `/speckit.checklist` | Generate quality validation checklists |
| `/speckit.implement` | Execute implementation from tasks |

### Required Commit Flow

1. **Create feature branch:** `git checkout -b ###-feature-name` (### matches spec number)
2. **Write spec:** `/speckit.specify` → commits `.specify/specs/###-feature-name/spec.md`
3. **Clarify:** `/speckit.clarify` → refine spec
4. **Plan:** `/speckit.plan` → commits plan, data-model, contracts, research
5. **Tasks:** `/speckit.tasks` → commits tasks.md with TDD ordering
6. **Implement:** `/speckit.implement` → runs tasks in order, writes tests first
7. **Verify:** All tests pass, coverage thresholds met
8. **PR:** Squash merge to `develop`, then `develop` → `main`

---

## Project Structure

```
.
├── .specify/                  # Spec Kit artifacts (COMMITTED)
│   ├── memory/
│   │   └── constitution.md    # Project governing principles
│   ├── specs/                 # Feature specifications
│   │   └── ###-feature-name/
│   │       ├── spec.md        # Functional specification
│   │       ├── plan.md        # Technical implementation plan
│   │       ├── tasks.md       # Task breakdown (TDD-ordered)
│   │       ├── data-model.md  # Domain model documentation
│   │       ├── contracts/     # API contracts
│   │       └── research.md    # Technical research decisions
│   ├── templates/             # Document templates
│   └── scripts/               # Automation scripts
├── src/
│   ├── main/java/com/enterprise/payment/
│   │   ├── domain/            # L0 - Core business entities & rules
│   │   ├── application/       # L1 - Use cases & ports
│   │   ├── infrastructure/    # L2 - External implementations
│   │   └── presentation/      # L3 - REST, GraphQL endpoints
│   └── test/                  # Mirrors main structure
├── AGENTS.md                  # THIS FILE — AI coding agent instructions
├── GEMINI.md                  # Gemini CLI context file
└── .gitignore                 # Comprehensive ignore rules
```

---

## Deployment Replicas — UNVIOLABLE

- **Minimum pods:** 2 (ALWAYS, under any circumstance. AT TEST STARTUP SHOULD BE EXACTLY = 2 PODS)
- **Maximum pods:** 30 (governed by HPA)
- HPA minReplicas is permanently locked at 2. Never scale to 1 or 0.
- When deploying locally or in any environment, always start with exactly 2 replicas.

### HPA Scaling Thresholds — IRON LAW

| Direction | Metric | Threshold |
|-----------|--------|-----------|
| UPSCALE | CPU | > 80% |
| UPSCALE | RAM | > 60% |
| DOWNSCALE | CPU | < 40% |
| DOWNSCALE | RAM | < 30% |

These thresholds are constitutional. Never modify them. Every k6 test must start with exactly 2 pods.

---

## Clean Architecture Rules

1. **Domain layer** (`src/main/java/.../domain/`) — Entities, value objects, domain services. ZERO framework annotations. Pure Java.
2. **Application layer** (`src/main/java/.../application/`) — Use cases (orchestration), ports (interfaces). Depends ONLY on domain.
3. **Infrastructure layer** (`src/main/java/.../infrastructure/`) — JPA repositories, external APIs, persistence adapters. Implements ports.
4. **Presentation layer** (`src/main/java/.../presentation/`) — REST controllers, GraphQL controllers. Calls use cases only.

**VIOLATION:** A domain entity importing `jakarta.persistence.*` is acceptable when using Spring Data JPA conventions. Port interfaces live in the application layer.

---

## Code Conventions

- Java 21. Spring Boot 3.2. Maven.
- No wildcard imports. Explicit imports only.
- No comments unless explaining WHY, not WHAT.
- Use records for DTOs and value objects where appropriate.
- Constructor injection (no `@Autowired` on fields).
- `@WithSpan` on all use case public methods for OpenTelemetry tracing.
- Pessimistic locking (`@Lock(PESSIMISTIC_WRITE)`) for wallet balance mutations.
- Optimistic locking (`@Version`) for payment state transitions.

---

## Gitflow Rules

| Branch | Purpose | Protected |
| :--- | :--- | :--- |
| `main` | Production releases | Yes |
| `develop` | Integration branch | Yes |
| `###-feature-name` | Feature development | No |
| `fix-###-description` | Bug fixes | No |
| `release-vX.Y.Z` | Release preparation | Yes |

- Feature branches are numbered by spec (e.g., `001-create-taskify`).
- Squash-merge PRs into `develop`. No merge commits.
- Tag releases: `v<major>.<minor>.<patch>`.
- Never force-push to `main` or `develop`.

---

## Build & Quality Gates

```bash
# Compile
mvn compile

# Run all tests
mvn test -Dspring.profiles.active=test

# Coverage report (must pass 100% thresholds for domain + application)
mvn verify

# Package
mvn package -DskipTests
```

- JaCoCo enforces **100% line + branch coverage** on `domain/**` and `application/**`.
- Build fails if coverage drops below thresholds.
- CI pipeline runs on every push and PR.
