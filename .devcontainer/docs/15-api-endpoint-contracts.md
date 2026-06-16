# Document #15: API Endpoint Contract Specification

## ⚠️ GLOBAL API CONTRACT RULES

### Contract Authority

This document is the **single source of truth** for all API behaviour.

All implementations MUST strictly conform to:

- endpoint paths
- HTTP methods
- request/response schemas
- status codes
- authorization rules

**No endpoint may exist without a defined contract.**

---

### Data Format Rules

- All endpoints use JSON request and response bodies unless explicitly stated otherwise
- All timestamps use ISO-8601 format
- All monetary values use decimal GBP representation

---

### Authorization Model

Authorization is **fully policy‑driven**. All access decisions are evaluated at runtime based on the `role` and `permission` tables defined in **DOC‑9C**.

- No role names or permissions are hardcoded in application logic.
- The backend enforces all authorization rules; frontend restrictions are informational only.
- The standard roles (Customer, Merchant, Admin, Auditor) and their permissions are defined as seed data, not as code constants.

> The specific role names mentioned in the endpoint descriptions below (e.g., "MERCHANT", "ADMIN") are **illustrative examples** of the required permissions. The actual enforcement must be based on the corresponding `permission` entries in the database.

---

### Idempotency Rules

Idempotency is enforced via `Idempotency-Key` header.

- Required for all state-changing POST endpoints
- Keys are client-generated and unique per operation
- Default TTL: 30 days (configurable)
- Duplicate requests MUST return the original response

---

### Error Model

All errors follow:

- HTTP status code
- Standardised error code (e.g. `AUTH-001`)
- Human-readable message

---

## Part 1: Endpoint Categories

| Category | Base Path | Purpose |
|----------|-----------|---------|
| Authentication | `/auth` | Login, logout, session management |
| Invoice Management | `/invoices` | Create, view, approve invoices |
| Payment Processing | `/payments` | Payments and refunds |
| Ledger | `/ledger` | Balances and transactions |
| Audit | `/audit` | Audit logs and verification |
| Reconciliation | `/admin/reconciliation` | Admin reconciliation jobs |
| Health | `/health` | System status |

---

## Part 2: Authentication

### POST /auth/login

| Field | Value |
|-------|-------|
| Authentication | None |
| Authorization | None |
| Idempotent | No |

#### Request Body

| Field | Type | Required |
|-------|------|----------|
| email | string | Yes |
| password | string | Yes |

#### Response (200 OK)

| Field | Type | Description |
|-------|------|-------------|
| userId | string | User identifier |
| role | string | User role (for UI display only – not for authorization) |

**Note:** Session is managed server-side (HTTP session cookie)

#### Errors

| Code | Status | Meaning |
|------|--------|---------|
| AUTH-001 | 401 | Invalid credentials |
| VALID-001 | 400 | Missing fields |

---

### POST /auth/logout

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Any authenticated user |
| Idempotent | Yes |

#### Response

`200 OK` (empty body)

---

## Part 3: Invoices

### POST /invoices

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `invoice:create` permission – typically granted to Merchant and Admin roles) |
| Idempotent | Yes |

#### Headers

| Header | Required |
|--------|----------|
| Idempotency-Key | Yes |

#### Request Body

| Field | Type | Required |
|-------|------|----------|
| amount | decimal | Yes |
| description | string | Yes |
| customerEmail | string | Yes |

#### Response (201 Created)

| Field | Type |
|-------|------|
| invoiceId | string |
| status | string |
| requiresApproval | boolean |

---

### GET /invoices

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (filters results based on `invoice:view_own` or `invoice:view_all` permissions) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| invoices | array |
| total | integer |

---

### POST /invoices/{invoiceId}/approve

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `invoice:approve` permission – typically granted to Admin role) |
| Idempotent | Yes |

#### Request Body

| Field | Type |
|-------|------|
| reason | string |

#### Response (200 OK)

| Field | Type |
|-------|------|
| status | string |
| approvedAt | timestamp |

---

## Part 4: Payments

### POST /payments

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `invoice:pay` permission – typically granted to Customer role) |
| Idempotent | Yes |

#### Request Body

| Field | Type |
|-------|------|
| invoiceId | string |
| paymentMethod | string |

#### Response (200 OK)

| Field | Type |
|-------|------|
| paymentId | string |
| status | string |

---

### POST /payments/{paymentId}/refund

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `payment:refund` permission – typically granted to Admin role) |
| Idempotent | Yes |

#### Request Body

| Field | Type |
|-------|------|
| reason | string |

#### Response (200 OK)

| Field | Type |
|-------|------|
| refundId | string |
| status | string |

---

## Part 5: Audit

### GET /audit/events

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `audit:view` permission – typically granted to Admin and Auditor roles) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| events | array |

---

### GET /audit/verify

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `audit:verify` permission – typically granted to Admin and Auditor roles) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| status | string |
| verifiedCount | integer |

---

## Part 6: Ledger

### GET /ledger/balances

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (access filtered by user context – own balances only, unless `ledger:view_all` permission exists) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| balances | array |

---

### GET /ledger/transactions

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (same as above) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| transactions | array |

---

## Part 7: Reconciliation

### POST /admin/reconciliation/run

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `reconciliation:run` permission – typically granted to Admin role) |
| Idempotent | Yes |

#### Response (202 Accepted)

| Field | Type |
|-------|------|
| jobId | string |
| status | string |

---

### GET /admin/reconciliation/jobs

| Field | Value |
|-------|-------|
| Authentication | Required |
| Authorization | Policy‑driven (requires `reconciliation:view_report` permission – typically granted to Admin role) |
| Idempotent | Yes |

#### Response (200 OK)

| Field | Type |
|-------|------|
| jobs | array |

---

## Part 8: Health

### GET /health/liveness

| Field | Value |
|-------|-------|
| Authentication | None |
| Authorization | None |
| Idempotent | Yes |

#### Response

`200 OK` (empty body)

---

### GET /health/readiness

| Field | Value |
|-------|-------|
| Authentication | None |
| Authorization | None |
| Idempotent | Yes |

#### Response

`200 OK` when ready, `503 Service Unavailable` when not ready

---

## Part 9: Idempotency Rules

| Rule | Behaviour |
|------|-----------|
| First request | Executes normally |
| Duplicate key | Returns cached response |
| Expired key | Treated as new request |
| Missing key | 400 error (`IDEM-001`) |

---

## Part 10: Error Codes

| Code | Meaning |
|------|---------|
| AUTH-001 | Invalid authentication |
| AUTH-002 | Insufficient role |
| VALID-001 | Validation error |
| INV-001 | Invoice not found |
| INV-002 | Invoice conflict |
| PAY-001 | Payment error |
| IDEM-001 | Duplicate request |
| SYS-001 | Internal error |

---

## Part 11: Document Control

| Field | Value |
|-------|-------|
| Document ID | DOC-15 |
| Version | 1.2 |
| Depends On | DOC-8A, DOC-9A, DOC-9C, DOC-10A |

---

**End of Document #15**