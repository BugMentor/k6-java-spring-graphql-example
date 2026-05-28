# API Contracts

## REST API — Base URL: `/v1`

### Users

```
POST   /v1/users          — Create user
GET    /v1/users          — List all users
GET    /v1/users/{id}     — Get user by ID
DELETE /v1/users/{id}     — Delete user
```

**Create User Request:**
```json
{
  "email": "user@example.com",
  "fullName": "John Doe",
  "status": "ACTIVE"
}
```
- `email` (required, string): Must contain `@`
- `fullName` (required, string): Cannot be blank
- `status` (optional, string): Defaults to `"ACTIVE"`

**Create User Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "fullName": "John Doe",
  "status": "ACTIVE",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

### Merchants

```
POST   /v1/merchants       — Create merchant
GET    /v1/merchants       — List all merchants
GET    /v1/merchants/{id}  — Get merchant by ID
DELETE /v1/merchants/{id}  — Delete merchant
```

**Create Merchant Request:**
```json
{
  "name": "Acme Corp",
  "apiKey": "sk-abc123"
}
```
- `name` (required, string): Merchant display name
- `apiKey` (required, string): Unique API key

**Create Merchant Response (201):**
```json
{
  "id": "660e8400-e29b-41d4-a716-446655440001",
  "name": "Acme Corp",
  "apiKey": "sk-abc123",
  "createdAt": "2024-01-01T00:00:00Z"
}
```

---

### Wallets

```
POST /v1/wallets             — Create wallet
GET  /v1/wallets             — List all wallets
GET  /v1/wallets/{id}        — Get wallet by ID
GET  /v1/wallets/user/{userId} — Get wallet by user ID
```

**Create Wallet Request:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "balance": 0.00,
  "currency": "USD"
}
```
- `userId` (required, UUID): Must reference existing user
- `balance` (optional, number): Defaults to `0.00`
- `currency` (optional, string): Defaults to `"USD"`

**Create Wallet Response (201):**
```json
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "user": { "id": "550e8400-e29b-41d4-a716-446655440000", "email": "user@example.com", "fullName": "John Doe", "status": "ACTIVE", "createdAt": "2024-01-01T00:00:00Z" },
  "balance": 0.00,
  "currency": "USD",
  "version": 0
}
```

---

### Payments

```
POST   /v1/payments                              — Create payment
POST   /v1/payments/wallet-transfer              — Wallet-to-merchant transfer
POST   /v1/payments/wallets/{id}/topup           — Top up wallet
POST   /v1/payments/batch                        — Batch payment creation
PUT    /v1/payments/{id}/refund                   — Refund payment
GET    /v1/payments/{id}                          — Get payment by ID
GET    /v1/payments/user/{userId}                 — List user payments
GET    /v1/payments/search                        — Dynamic search
GET    /v1/payments/reports/summary               — Payment summary report
```

**Create Direct Payment Request:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "merchantId": "660e8400-e29b-41d4-a716-446655440001",
  "amount": 100.00,
  "type": "DEBIT",
  "status": "PENDING"
}
```

**Create Direct Payment Response (201):**
```json
{
  "id": "880e8400-e29b-41d4-a716-446655440003",
  "user": { "id": "550e8400...", ... },
  "merchant": { "id": "660e8400...", ... },
  "wallet": null,
  "amount": 100.00,
  "type": "DEBIT",
  "status": "PENDING",
  "createdAt": "2024-01-01T00:00:00Z",
  "version": 0
}
```

**Wallet Transfer Request:**
```json
{
  "walletId": "770e8400-e29b-41d4-a716-446655440002",
  "merchantId": "660e8400-e29b-41d4-a716-446655440001",
  "amount": 50.00
}
```
- Uses pessimistic lock on wallet
- Automatically sets `type: "WALLET_TRANSFER"`, `status: "SUCCESS"`
- Returns Payment entity

**Wallet Top-Up:**
- Body: Raw number (e.g., `500.00`)
- URL: `/v1/payments/wallets/{walletId}/topup`
- Uses pessimistic lock on wallet
- Returns updated Wallet entity
- Rejects zero and negative amounts (400)

**Batch Payments:**
```json
[
  { "userId": "...", "merchantId": "...", "amount": 10.00, "type": "DEBIT", "status": "PENDING" },
  { "userId": "...", "merchantId": "...", "amount": 20.00, "type": "DEBIT", "status": "PENDING" }
]
```
- Maximum 500 items per batch
- Returns 202 Accepted (no body)

**List User Payments:**
```
GET /v1/payments/user/{userId}?status=SUCCESS&limit=10
```
- `status` (optional): Filter by status, defaults to `"SUCCESS"`
- `limit` (optional): Max results, defaults to `10`

**Dynamic Search:**
```
GET /v1/payments/search?minAmount=10&maxAmount=500&type=DEBIT&status=SUCCESS&page=0&size=10
```
- All parameters optional
- `page` defaults to `0`, `size` defaults to `10`

**Payment Summary Report:**
```
GET /v1/payments/reports/summary?startDate=2024-01-01T00:00:00Z&endDate=2024-01-31T23:59:59Z
```
Response:
```json
{
  "totalsByStatus": {
    "SUCCESS": 1500.00,
    "PENDING": 200.00,
    "REFUNDED": 50.00
  }
}
```

**Refund Payment:**
```
PUT /v1/payments/{id}/refund
```
- Only `SUCCESS` payments can be refunded
- Returns refunded Payment with status `"REFUNDED"`
- Refunding an already `REFUNDED` payment returns 422

## GraphQL API — Base URL: `/graphql`

**Endpoint:** `POST /graphql` (also supports GET for queries)
**Schema:** Available via introspection at `POST /graphql`

### Mutations

**Process Payment:**
```graphql
mutation {
  processPayment(input: {
    userId: "550e8400-e29b-41d4-a716-446655440000"
    merchantId: "660e8400-e29b-41d4-a716-446655440001"
    amount: 100.00
    type: "DEBIT"
  }) {
    id
    amount
    type
    status
    createdAt
  }
}
```

**Wallet Transfer:**
```graphql
mutation {
  walletTransfer(
    walletId: "770e8400-e29b-41d4-a716-446655440002"
    merchantId: "660e8400-e29b-41d4-a716-446655440001"
    amount: 50.00
  ) {
    id
    amount
    status
  }
}
```

**Top Up Wallet:**
```graphql
mutation {
  topUpWallet(walletId: "770e8400-e29b-41d4-a716-446655440002", amount: 500.00) {
    id
    balance
    currency
  }
}
```

**Refund Payment:**
```graphql
mutation {
  refundPayment(id: "880e8400-e29b-41d4-a716-446655440003") {
    id
    status
    amount
  }
}
```

**Batch Process Payments:**
```graphql
mutation {
  processBatchPayments(payments: [
    { userId: "...", merchantId: "...", amount: 10.00, type: "DEBIT" }
    { userId: "...", merchantId: "...", amount: 20.00, type: "DEBIT" }
  ])
}
```

**Create User:**
```graphql
mutation {
  createUser(email: "user@example.com", fullName: "John Doe", status: "ACTIVE") {
    id
    email
    fullName
    status
    createdAt
  }
}
```

**Create Wallet:**
```graphql
mutation {
  createWallet(userId: "...", balance: 0.00, currency: "USD") {
    id
    userId
    balance
    currency
  }
}
```

**Create Merchant:**
```graphql
mutation {
  createMerchant(name: "Acme Corp", apiKey: "sk-abc123") {
    id
    name
    apiKey
    createdAt
  }
}
```

### Queries

**Get Payment:**
```graphql
query {
  payment(id: "880e8400-e29b-41d4-a716-446655440003") {
    id
    userId
    merchantId
    amount
    type
    status
    createdAt
  }
}
```

**List User Payments:**
```graphql
query {
  payments(userId: "550e8400-...", status: "SUCCESS", limit: 10) {
    id
    amount
    status
    createdAt
  }
}
```

**Search Payments:**
```graphql
query {
  searchPayments(minAmount: 10, maxAmount: 500, status: "SUCCESS", page: 0, size: 10) {
    id
    amount
    type
    status
    createdAt
  }
}
```

**Payment Summary Report:**
```graphql
query {
  paymentSummary(startDate: "2024-01-01T00:00:00Z", endDate: "2024-01-31T23:59:59Z") {
    totalsByStatus {
      status
      total
    }
  }
}
```

**Entity Queries:**
```graphql
query {
  user(id: "...") { id email fullName status createdAt }
  users { id email fullName status createdAt }
  wallet(id: "...") { id userId balance currency version }
  wallets { id userId balance currency version }
  walletByUserId(userId: "...") { id userId balance currency version }
  merchant(id: "...") { id name apiKey createdAt }
  merchants { id name apiKey createdAt }
}
```
```
