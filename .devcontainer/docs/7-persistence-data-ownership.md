# Document #7: Persistence & Data Ownership Specification

## Purpose

This document defines:

- authoritative persistence ownership,
- data responsibility boundaries,
- persistence consistency guarantees,
- operational storage rules,
- transactional persistence expectations,
- and infrastructure authority boundaries.

This document explains:

- what data exists,
- where authoritative state lives,
- which module owns which records,
- what persistence guarantees must exist,
- and what storage responsibilities are forbidden.

This document is NOT:

- a database schema,
- a migration file,
- an ORM implementation guide,
- a repository implementation,
- or a deployment specification.

---

# Part 1: Persistence Principles

The platform persistence model follows the principles below.

| Principle | Meaning |
|---|---|
| Authoritative persistence | Persistent business state must have a single authoritative source |
| Strong consistency | Critical business state must remain transactionally consistent |
| Append-only history | Historical operational records are never mutated |
| Explicit ownership | Each persistent record belongs to one owning module |
| Single ownership | Each record has one authoritative owning module |
| Infrastructure isolation | Business logic must not depend directly on storage technology |
| Deterministic persistence | Valid operations must produce predictable stored state |
| Operational recoverability | Failures must remain traceable and recoverable |

Single ownership applies at the record level, not the workflow level.

A payment workflow may coordinate updates across Transaction, Invoice, Ledger, and Audit records — each record remains owned by its respective module, but the Transaction Module orchestrates the workflow.

---

# Part 2: Persistence Authority Boundaries

## PostgreSQL Responsibilities

PostgreSQL is the authoritative system of record for:

- invoices,
- transactions,
- ledger entries,
- audit events,
- approvals,
- refunds,
- reconciliation records,
- user accounts,
- roles,
- permissions,
- and authorization-related business state.

PostgreSQL owns all internal transaction simulation state — including:

- virtual settlement statuses,
- approval records,
- refund records,
- reconciliation checkpoints,
- and operational transaction lifecycle history.

No external payment provider state (e.g. Stripe webhook payloads, payment processor events, external settlement IDs) is persisted because the platform implements a fully self-contained virtual transaction engine as defined in DOC-001.

---

## Redis Responsibilities

Redis is permitted ONLY for:

- short-lived caching of idempotency keys (performance optimisation only),
- temporary retry coordination,
- operational throttling,
- ephemeral notification state,
- and temporary workflow coordination.

**Idempotency Storage Rule**:
- The authoritative idempotency key store is a dedicated PostgreSQL table with a unique constraint on the key.
- Redis may cache the result (key → outcome) with a configurable TTL.
- If Redis loses a cached key, the system falls back to the PostgreSQL table.
- A cache miss does not affect correctness – only performance.
- Redis failures MUST NOT cause duplicate transaction execution.

Redis must NEVER become the authoritative owner of:
- transaction history,
- balances,
- audit history,
- authorization state,
- invoice lifecycle state,
- or reconciliation records.


# Part 3: Module Persistence Ownership

Each module owns its own persistent records.

Cross-module persistence modification must occur only through controlled service orchestration.

| Module | Owned Records |
|---|---|
| Identity & Access Module | Users, roles, permissions, authentication metadata |
| Invoice Module | Invoices, invoice statuses, invoice approval requirements, **approval records** |
| Transaction Module | Transactions, transaction lifecycle state, refunds |
| Ledger Module | Ledger entries, operational balance state |
| Audit Module | Audit events, hash chains, verification metadata |
| Reconciliation Module | Reconciliation records, mismatch reports |
| Notification Module | Notification delivery records and retry state |

**Note**: Approval records are owned by the Invoice Module, not a separate "Workflow Module". This aligns with DOC-003 Part 4 and DOC-006 Part 2.

---

# Part 4: Persistence Guarantees

## Transaction Guarantees

The platform guarantees:

| Guarantee | Meaning |
|---|---|
| Atomic persistence | Related business changes succeed or fail together |
| Consistent state transitions | Invalid lifecycle states cannot persist |
| Durable commits | Committed state survives system restart |
| Deterministic updates | Same valid operation produces same resulting state |
| Isolation | Concurrent operations cannot corrupt authoritative state |

---

## Audit Guarantees

The audit subsystem guarantees:

| Guarantee | Meaning |
|---|---|
| Immutable history | Audit records cannot be modified |
| Append-only persistence | New records are appended only |
| Tamper evidence | Historical modification attempts are detectable |
| Historical traceability | Operational history remains visible |
| Verification capability | Audit integrity can be independently verified |

---

## Ledger Guarantees

The ledger subsystem guarantees:

| Guarantee | Meaning |
|---|---|
| Immutable ledger entries | Ledger history cannot be rewritten |
| Transactional consistency | Balance mutations remain coordinated |
| Traceable balance movement | Every balance mutation has historical visibility |
| Operational recoverability | Ledger inconsistencies can be investigated |

---

# Part 5: Cross-Module Persistence Rules

## Allowed Cross-Module Coordination

Cross-module workflows are permitted when orchestrated through controlled domain services.

Example:

Payment execution may coordinate:

- invoice validation,
- transaction persistence,
- ledger updates,
- and audit event generation

within a single controlled orchestration workflow.

---

## Forbidden Persistence Patterns

The following persistence patterns are forbidden.

| Forbidden Pattern | Reason |
|---|---|
| Direct table access across modules | Violates ownership boundaries |
| Shared mutable business state | Creates unclear authority |
| Business logic inside repositories | Violates separation of concerns |
| Infrastructure-aware domain logic | Couples business logic to storage technology |
| Direct Redis dependency from domain logic | Violates persistence abstraction |
| Cross-module repository injection | Breaks modular isolation |

---

# Part 6: Persistence Workflow Semantics

## Payment Workflow Persistence

The payment workflow guarantees:

- invoice validation occurs before settlement persistence,
- duplicate requests do not create duplicate successful transaction state,
- settlement updates remain transactionally coordinated,
- and audit history persists alongside workflow execution.

---

## Refund Workflow Persistence

The refund workflow guarantees:

- refunds persist as explicit operational events,
- historical settlement activity remains visible,
- refund persistence does not erase historical transaction state,
- and refund execution remains fully auditable.

---

## Reconciliation Persistence

The reconciliation subsystem guarantees:

- verification records remain historically visible,
- mismatches are recorded rather than silently corrected,
- reconciliation history remains append-only,
- and reconciliation execution remains independent from transaction execution.

---

# Part 7: Persistence Isolation Rules

The platform enforces strict isolation boundaries between business logic and infrastructure implementation.

| Layer | Responsibility |
|---|---|
| Domain layer | Business rules and orchestration |
| Persistence layer | Storage implementation |
| Infrastructure layer | Database, cache, and external integrations |
| Application layer | Request coordination and transport concerns |

Domain logic must depend only on interfaces — never on storage implementation details.

---

## Part 8: Transaction Boundary Rules

### Transactional Coordination

When workflows span multiple modules:

- related state changes must commit atomically where possible,
- authoritative business state must remain consistent,
- and partial persistence failures must not leave invalid operational state.

### Audit Persistence Rule (REQUIRED)

Audit events are persisted in a **separate logical transaction** to isolate audit generation from side effects of the main business transaction.

**Implementation pattern (Spring)**:

1. The main workflow method is marked `@Transactional`.
2. Audit persistence is delegated to a separate service method annotated with `@Transactional(propagation = Propagation.REQUIRES_NEW)`.
3. If the audit method throws a `DataAccessException` (or any exception), the caller catches it, **marks the current transaction for rollback** via `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`, and rethrows a business exception.
4. The main transaction then rolls back. **No state mutation is committed without a corresponding audit record.**

**Failure flow**:
- Audit insert fails → main transaction rolled back → user receives 500 error → operational alert raised.
- It is **forbidden** to commit the main transaction if audit insertion fails.

**Code example** (illustrative, not prescriptive) is provided in the module implementation guide.

# Part 9: Operational Recovery Expectations

Persistence architecture must support:

- restart-safe recovery,
- transactional rollback,
- retry-safe workflows,
- idempotent recovery operations,
- reconciliation verification,
- and operational investigation.

Persistence design must prioritize recoverability over implementation convenience.

---

# Part 10: Persistence Anti-Goals

The persistence architecture intentionally avoids:

| Anti-Goal | Reason |
|---|---|
| Event sourcing everywhere | Adds unnecessary architectural complexity |
| Distributed transactions | Outside project scope |
| Shared database ownership | Violates modular authority |
| Eventually consistent balances | Conflicts with consistency guarantees |
| Infrastructure-driven business rules | Violates architectural boundaries |
| External payment provider persistence | Conflicts with self-contained transaction engine philosophy |

---

# Part 11: Glossary

| Term | Meaning |
|---|---|
| Authoritative state | Official source of business truth |
| Atomic persistence | Related updates succeed or fail together |
| Append-only | Historical records are never modified |
| Idempotency | Duplicate requests safely produce same outcome |
| Reconciliation | Verification that operational state remains consistent |
| Tamper-evident | Historical modification attempts can be detected |
| Operational coordination state | Temporary state used for workflow coordination only |

---

# Part 12: Document Control

| Field | Value |
|---|---|
| Document ID | DOC-7 |
| Version | 2.1 |
| Applies To | ChatGPT |
| Depends On | DOC-1, DOC-4, DOC-5 |

---

**End of Document #7**