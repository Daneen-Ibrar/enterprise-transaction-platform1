# DOC-8B: Transactional Behaviour, Error Semantics & Operational Observability

## Purpose

This document defines:

* transactional ownership rules,
* idempotent request guarantees,
* concurrency protection constraints,
* error handling semantics,
* failure visibility requirements,
* operational observability standards,
* endpoint operational behaviour,
* and forbidden application-layer patterns.

This document extends:

* DOC-3 (System Architecture),
* DOC-4 (Architectural Decision Records),
* DOC-5 (Domain Model & Lifecycle),
* and DOC-8A (API Transport & Application Boundaries).

This document exists to ensure:

* transactional integrity remains centralized,
* workflows remain deterministic,
* operational failures remain diagnosable,
* retries remain safe,
* and platform behaviour remains observable and auditable.

---

# Part 1: Transaction Ownership Rules

Transactional boundaries are owned by:

> Domain Workflow Execution.

The Application Layer may:

* delegate execution,
* propagate correlation identifiers,
* and map responses.

However:

> business transaction ownership remains a Domain concern.

---

## Transaction Ownership Principles

| Principle                 | Meaning                                                |
| ------------------------- | ------------------------------------------------------ |
| Centralized ownership     | Transaction boundaries remain domain-owned             |
| Atomic execution          | Related mutations succeed or fail together             |
| Deterministic transitions | Invalid state movement rejected                        |
| Ledger consistency        | Financial state remains synchronized                   |
| Audit participation       | Audit generation remains transactional                 |
| Persistence abstraction   | Transactional persistence hidden behind infrastructure |

---

## Controllers MUST NEVER

Controllers must NEVER:

* open transactions,
* manage rollback behaviour,
* coordinate persistence sequencing,
* manipulate repositories directly,
* or coordinate transaction retries.

Controllers are transport adapters only.

---

## Application Layer MUST NEVER

The Application Layer must NEVER:

* own transactional sequencing,
* coordinate workflow rollback,
* mutate ledger state,
* manage settlement ordering,
* or coordinate transactional persistence.

Primary orchestration remains owned by:

> Domain Services.

---

## Infrastructure Responsibility

Infrastructure frameworks may provide:

* transaction mechanisms,
* persistence coordination,
* rollback implementations,
* and repository abstractions.

However:

> transactional business ownership remains domain-controlled.

This aligns with:

* ADR-018,
* DOC-3,
* and DOC-5 transaction invariants.

---

# Part 2: Transactional Consistency Guarantees

The platform enforces strong transactional consistency guarantees.

---

## Consistency Guarantees

| Guarantee                 | Meaning                                           |
| ------------------------- | ------------------------------------------------- |
| Atomic persistence        | Related state changes succeed or fail together    |
| Ledger integrity          | Balances remain synchronized                      |
| Deterministic transitions | Invalid state movement rejected                   |
| Audit consistency         | Audit state reflects workflow state               |
| Idempotent execution      | Duplicate requests remain safe                    |
| Concurrency protection    | Simultaneous duplicate requests remain controlled |

---

## Transaction Lifecycle Integrity

Transactional workflows must preserve:

* state consistency,
* ledger consistency,
* audit consistency,
* and deterministic lifecycle progression.

Partial workflow mutation is forbidden.

The system must never allow:

* orphaned ledger records,
* partially committed transaction flows,
* inconsistent audit chains,
* or invalid lifecycle transitions.

---

## Required Transactional Pattern

```text
Workflow Execution
      ↓
Transactional State Mutation
      ↓
Audit Event Generation
      ↓
Transactional Persistence Finalization
```

Audit generation occurs in a separate transaction. If audit generation fails, the main transaction MUST roll back. No state mutation may commit without a corresponding audit record

Audit generation must never occur:

* asynchronously from transaction mutation,
* outside workflow boundaries,
* or independently from persistence finalization.

---

# Part 3: Idempotent Request Behaviour

The platform must support:

> safe retry behaviour.

Idempotency is mandatory for:

* payment execution,
* refund execution,
* settlement operations,
* and transaction lifecycle workflows.

---

## Idempotency Guarantees

| Guarantee                            | Meaning                                         |
| ------------------------------------ | ----------------------------------------------- |
| Duplicate requests remain safe       | Duplicate submissions do not duplicate outcomes |
| Concurrent duplicate protection      | Simultaneous retries cannot corrupt state       |
| Deterministic retry behaviour        | Same request returns predictable result         |
| Cached responses permitted           | Existing workflow result may be returned        |
| Verification occurs before execution | Duplicate detection occurs early                |
| Retry history remains traceable      | Operational visibility preserved                |

---

## Idempotency Workflow

```text
Request Received
      ↓
Idempotency Key Present?
      ↓
YES → Existing Result Found?
           ↓
      YES → Return Existing Response
           ↓
      NO → Continue Transaction Workflow
```

---

## Idempotency Constraints

Idempotency keys are stored in PostgreSQL with a unique constraint on the transaction table. Redis is used only as a performance cache; if Redis misses the key, the database is checked before executing a new transaction.

Idempotency verification must occur:

* before workflow execution,
* before ledger mutation,
* before audit generation,
* and before persistence finalization.

Idempotency enforcement must remain:

* deterministic,
* observable,
* and operationally traceable.

---

## Forbidden Idempotency Patterns

| Forbidden Pattern                    | Reason                          |
| ------------------------------------ | ------------------------------- |
| Post-persistence duplicate detection | Duplicate state corruption risk |
| Frontend-only retry prevention       | Backend safety violation        |
| Non-deterministic retry behaviour    | Inconsistent workflow outcomes  |
| Missing retry traceability           | Operational visibility failure  |

---

# Part 4: Concurrency Protection

The platform must protect against:

* concurrent duplicate submissions,
* race-condition-driven state mutation,
* inconsistent ledger updates,
* and conflicting lifecycle transitions.

---

## Concurrency Guarantees

| Guarantee                        | Meaning                                    |
| -------------------------------- | ------------------------------------------ |
| Duplicate concurrency protection | Parallel retries cannot duplicate outcomes |
| Lifecycle transition protection  | Invalid concurrent state mutation rejected |
| Ledger synchronization           | Balance state remains consistent           |
| Deterministic conflict handling  | Conflicting operations fail predictably    |
| Workflow isolation               | Concurrent execution remains controlled    |

---

## Concurrency Failure Examples

| Scenario                        | Expected Behaviour              |
| ------------------------------- | ------------------------------- |
| Duplicate payment retries       | Existing result returned        |
| Concurrent refund attempts      | Invalid duplicate rejected      |
| Simultaneous lifecycle mutation | Invalid transition rejected     |
| Concurrent settlement execution | Consistency protection enforced |

---

## Forbidden Concurrency Patterns

The platform must NEVER allow:

* uncontrolled concurrent mutation,
* duplicate financial execution,
* race-condition-driven lifecycle corruption,
* or inconsistent ledger visibility.

---

# Part 5: Error Handling Principles

The platform treats error handling as:

> a first-class architectural concern.

Failures must remain:

* controlled,
* diagnosable,
* traceable,
* and operationally visible.

---

## Error Handling Guarantees

| Guarantee                    | Meaning                                    |
| ---------------------------- | ------------------------------------------ |
| Controlled failure responses | Internal implementation details not leaked |
| Standardized error structure | Failures remain predictable                |
| Validation visibility        | Invalid requests clearly identified        |
| Authorization visibility     | Forbidden operations clearly rejected      |
| Correlation visibility       | Failures traceable across workflows        |
| Operational traceability     | Important failures observable              |

---

## Failure Categories

| Category               | Example                          |
| ---------------------- | -------------------------------- |
| Validation Failure     | Missing payment amount           |
| Authorization Failure  | Unauthorized refund attempt      |
| Workflow Failure       | Invalid transaction transition   |
| Persistence Failure    | Database rollback                |
| Infrastructure Failure | Redis unavailable                |
| Concurrency Failure    | Duplicate simultaneous request   |
| Operational Failure    | Notification service unavailable |

---

## Failure Visibility Requirements

Failures must generate:

* structured logs,
* correlation identifiers,
* operational traceability,
* and audit visibility where appropriate.

The system must NEVER:

* silently swallow operational failures,
* expose internal stack traces publicly,
* or partially corrupt transactional state.

---

## Error Response Constraints

Error responses must remain:

* deterministic,
* transport-safe,
* authorization-safe,
* and operationally controlled.

Internal infrastructure implementation details must remain hidden.

---

# Part 6: Operational Observability

The platform must support:

> end-to-end operational traceability.

All important workflows must remain:

* diagnosable,
* observable,
* traceable,
* and operationally visible.

---

## Observability Requirements

| Requirement                       | Meaning                               |
| --------------------------------- | ------------------------------------- |
| Correlation IDs required          | Requests remain traceable             |
| Correlation propagation required  | Traceability preserved across modules |
| Structured logging required       | Logs remain machine-readable          |
| Workflow visibility required      | Transaction flows remain observable   |
| Failure visibility required       | Operational issues remain diagnosable |
| Authorization visibility required | Sensitive operations remain traceable |

---

## Correlation Propagation

Correlation identifiers must propagate across:

* controller boundaries,
* domain workflow execution,
* infrastructure integration boundaries,
* and operational logging.

This preserves:

* operational diagnosis,
* distributed traceability,
* and workflow observability.

---

## Logging Constraints

Application-layer logging must NEVER:

* leak secrets,
* expose credentials,
* bypass audit visibility,
* or expose sensitive internal state.

Logging must remain:

* structured,
* centralized,
* correlation-aware,
* and operationally searchable.

---

# Part 7: Operational Endpoint Behaviour

The platform exposes operational endpoint categories.

Operational endpoints must remain:

* authorization-protected,
* traceable,
* observable,
* and operationally controlled.

---

## Conceptual Endpoint Categories

| Category                  | Purpose                            | Examples                   |
| ------------------------- | ---------------------------------- | -------------------------- |
| Authentication            | Identity and session workflows     | /login, /logout            |
| Invoice Management        | Invoice lifecycle operations       | /invoices                  |
| Payment Processing        | Payment and refund execution       | /payments                  |
| Ledger Queries            | Transaction and balance visibility | /ledger                    |
| Audit & Verification      | Audit integrity visibility         | /audit/events              |
| Administrative Operations | Operational job execution          | /admin/reconciliation/jobs |
| Health & Monitoring       | Operational monitoring             | /health, /metrics          |

---

## Operational Endpoint Constraints

Privileged operational endpoints must:

* require elevated authorization,
* remain operationally visible,
* generate audit visibility,
* and remain traceable through correlation identifiers.

Operational execution must NEVER:

* bypass authorization,
* bypass audit visibility,
* or mutate transactional state outside workflow constraints.

---

# Part 8: Forbidden Application Patterns

The following patterns are forbidden.

| Forbidden Pattern                  | Reason                           |
| ---------------------------------- | -------------------------------- |
| Fat controllers                    | Business logic duplication       |
| Repository access from controllers | Layer violation                  |
| Controller-owned transactions      | Transaction ownership leakage    |
| Secondary orchestration services   | Domain ownership conflict        |
| Business logic inside DTOs         | Separation-of-concerns violation |
| Frontend-only authorization        | Security bypass risk             |
| Transport-aware domain models      | Boundary corruption              |
| Domain entity serialization        | API-domain coupling              |
| Uncontrolled concurrent mutation   | Transaction corruption risk      |
| Non-deterministic retry handling   | Workflow inconsistency           |

---

# Part 9: Architectural Summary

The platform architecture preserves:

* centralized domain orchestration,
* deterministic workflow execution,
* transactional integrity,
* operational observability,
* audit consistency,
* idempotent retry behaviour,
* and strict transport isolation.

The Application Layer exists to:

* coordinate transport concerns,
* enforce authorization entry,
* standardize responses,
* and delegate execution into domain workflows.

Primary orchestration remains owned by:

> Domain Services.

This preserves:

* architectural consistency,
* operational reliability,
* auditability,
* maintainability,
* and enterprise-grade workflow discipline.

---

### Correlation Header Specification

- **Header name**: `X-Correlation-ID` (case‑insensitive)
- **Generation**:
  - If the header is absent, the application generates a new UUIDv7 (or UUIDv4 if v7 unavailable).
  - Generated ID is added to the MDC (Mapped Diagnostic Context) and all log entries.
- **Propagation**:
  - Outgoing infrastructure calls (JDBC, Redis) do not automatically propagate; internal thread‑local propagation is sufficient.
  - For future async boundaries, the ID must be explicitly passed.
- **Log format**: Structured logs (JSON) contain a field `correlationId`.

# Part 10: Alignment With Previous Documents

| Document | Relationship                                       |
| -------- | -------------------------------------------------- |
| DOC-3  | Defines architecture and orchestration ownership   |
| DOC-4  | Defines architectural decisions and tradeoffs      |
| DOC-5  | Defines lifecycle invariants and transaction rules |
| DOC-6  | Defines module ownership boundaries                |
| DOC-7  | Defines persistence ownership constraints          |
| DOC-8A | Defines transport and application boundaries       |

---

# Part 11: Document Control

| Field       | Value                    |
| ----------- | ------------------------ |
| Document ID | DOC-8B                 |
| Version     | 1.0                      |
| Applies To  | ChatGPT                  |
| Depends On  | DOC-3 through DOC-8A |

---

**End of DOC-008B**
