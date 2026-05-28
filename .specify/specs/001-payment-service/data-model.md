# Data Model

## Entity-Relationship Diagram

```
User (1) ──── (1) Wallet
  │                │
  │                │
  │                │
  ▼                ▼
Payment ──── (1) Wallet (nullable)
  │
  ▼
Merchant (1) ──► Payment
```

## Entities

### User
| Field | Type | Column | Constraints |
|-------|------|--------|-------------|
| `id` | `UUID` | PK | Auto-generated if null |
| `email` | `String` | NOT NULL, UNIQUE | Must contain `@` |
| `fullName` | `String` | `full_name`, NOT NULL | Cannot be blank |
| `status` | `String` | NOT NULL | Defaults to `"ACTIVE"` |
| `createdAt` | `Instant` | `created_at`, NOT NULL | Auto-set to `Instant.now()` |

**Domain rules:**
- Email must contain `@` character
- Full name cannot be null or blank
- Status defaults to `"ACTIVE"` if not provided
- `createdAt` is always set to current instant on construction

### Merchant
| Field | Type | Column | Constraints |
|-------|------|--------|-------------|
| `id` | `UUID` | PK | Auto-generated if null |
| `name` | `String` | NOT NULL | |
| `apiKey` | `String` | `api_key`, NOT NULL, UNIQUE | |
| `createdAt` | `Instant` | `created_at`, NOT NULL | Auto-set to `Instant.now()` |

**Domain rules:**
- API key must be unique across all merchants
- `createdAt` is always set to current instant on construction

### Wallet
| Field | Type | Column | Constraints |
|-------|------|--------|-------------|
| `id` | `UUID` | PK | Auto-generated if null |
| `user` | `User` | `user_id`, FK, NOT NULL, UNIQUE | One-to-One with User |
| `balance` | `BigDecimal` | NOT NULL | Defaults to `BigDecimal.ZERO` |
| `currency` | `String` | NOT NULL | |
| `version` | `Long` | `@Version` | Optimistic locking |

**Domain rules:**
- `debit(BigDecimal amount)`: Subtracts from balance. Throws if amount <= 0 or insufficient funds
- `topUp(BigDecimal amount)`: Adds to balance. Throws if amount <= 0
- A User can have at most one Wallet
- Version field is managed automatically by JPA for optimistic locking

### Payment
| Field | Type | Column | Constraints |
|-------|------|--------|-------------|
| `id` | `UUID` | PK | Auto-generated if null |
| `user` | `User` | `user_id`, FK, NOT NULL | Many-to-One |
| `merchant` | `Merchant` | `merchant_id`, FK, NOT NULL | Many-to-One |
| `wallet` | `Wallet` | `wallet_id`, FK, nullable | Many-to-One |
| `amount` | `BigDecimal` | NOT NULL | |
| `type` | `String` | NOT NULL | `"DEBIT"`, `"WALLET_TRANSFER"`, etc. |
| `status` | `String` | NOT NULL | `"PENDING"`, `"SUCCESS"`, `"REFUNDED"` |
| `createdAt` | `Instant` | `created_at`, NOT NULL | Auto-set to `Instant.now()` |
| `version` | `Long` | `@Version` | Optimistic locking |

**Domain rules:**
- Status defaults to `"PENDING"` if not provided
- `refund()`: Creates a new Payment with status `"REFUNDED"` — only allowed if current status is `"SUCCESS"`. Throws `IllegalStateException` otherwise
- Version field is managed automatically by JPA for optimistic locking on state transitions

## Value Objects

### PaymentSummary
| Field | Type |
|-------|------|
| `totalsByStatus` | `Map<String, BigDecimal>` |

Aggregated totals grouped by payment status for a given date range.

## State Machine

```
PENDING ──► SUCCESS ──► REFUNDED
```

- Payments start as `PENDING`
- Wallet transfers auto-complete to `SUCCESS`
- `SUCCESS` payments can be refunded to `REFUNDED`
- `PENDING` payments cannot be refunded
- Once `REFUNDED`, a payment cannot be refunded again
