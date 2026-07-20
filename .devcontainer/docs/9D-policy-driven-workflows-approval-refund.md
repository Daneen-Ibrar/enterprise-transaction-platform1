# Document #9D: Policy-Driven Workflow Rules (Approval & Refund)

## Purpose

This document defines database tables and evaluation rules for **approval workflows** (Invoice Module) and **refund eligibility** (Transaction Module). All decisions must be policy‑driven; hardcoded thresholds or role checks are forbidden.

This document extends DOC‑9C (Policy‑Driven Authorization Schema) and aligns with DOC‑10A (Reliability Policy Engine).

---

# Part 1: Approval Rule Table (owned by Invoice Module)

## `approval_rule`

| Column               | Type                           | Description                                 |
|----------------------|--------------------------------|---------------------------------------------|
| id                   | BIGINT PRIMARY KEY             | Surrogate key                               |
| rule_priority        | INT NOT NULL                   | Evaluation order (lower = higher priority)  |
| condition_expression | TEXT NOT NULL                  | SpEL or JSON DSL expression                 |
| required_permission  | VARCHAR(100) NOT NULL          | Permission required to approve              |
| active               | BOOLEAN NOT NULL DEFAULT TRUE  | Rule activation flag                        |
| created_at           | TIMESTAMP NOT NULL             |                                             |

**Example condition (SpEL)**:  
`#invoice.amount > 5000 and #merchant.riskLevel == 'HIGH'`

**Evaluation behaviour**:
- Rules are evaluated in ascending `rule_priority`.
- The first matching rule determines the required permission.
- If no rule matches, the invoice does **not** require approval.
- If a matching rule specifies a permission that the current user does not have, the approval action is denied.

---

# Part 2: Refund Rule Table (owned by Transaction Module)

## `refund_rule`

| Column               | Type                           | Description                                 |
|----------------------|--------------------------------|---------------------------------------------|
| id                   | BIGINT PRIMARY KEY             |                                             |
| rule_priority        | INT NOT NULL                   | Evaluation order                            |
| condition_expression | TEXT NOT NULL                  | Evaluated against transaction context       |
| action               | VARCHAR(30) NOT NULL           | `ALLOW`, `REQUIRE_APPROVAL`, `DENY`         |
| required_permission  | VARCHAR(100) NULL              | Permission if approval required             |
| active               | BOOLEAN NOT NULL DEFAULT TRUE  |                                             |
| created_at           | TIMESTAMP NOT NULL             |                                             |

**Action semantics**:
- `ALLOW` – refund processed immediately.
- `REQUIRE_APPROVAL` – refund requires a separate approval workflow (the `required_permission` is checked against the current user).
- `DENY` – refund rejected.

**Evaluation behaviour**:
- Rules evaluated in ascending `rule_priority`.
- First matching rule determines the action.
- If no rule matches, default is `DENY` (fail secure).

---

# Part 3: Runtime Evaluation Engine

- PostgreSQL is the **source of truth** for both tables.
- Optional Redis caching (TTL configurable) is permitted – cache misses fall back to the database.
- Missing or invalid rules (e.g., malformed condition expression) result in a safe default. If no approval rule matches, the invoice does not require approval. Invalid or malformed approval rules must not bypass authorization and should require manual review or policy correction. For refunds, invalid or malformed rules default to `DENY`.
- All policy evaluations are **logged** and **audited** (see DOC‑9B).

---

# Part 4: Alignment with Existing Documents

| Document | Relationship |
|----------|--------------|
| DOC‑6 | Module ownership (Invoice Module, Transaction Module) |
| DOC‑9A | Authorization framework |
| DOC‑9C | Extension of policy‑driven schema |
| DOC‑10A | Reliability policies (retry, DLQ) – separate from workflow rules |

---

# Part 5: Document Control

| Field | Value |
|-------|-------|
| Document ID | DOC‑9D |
| Version | 1.0 |
| Type | Security & Workflow Policy |
| Depends On | DOC‑6, DOC‑9A, DOC‑9C, DOC‑10A |

**End of DOC‑9D**