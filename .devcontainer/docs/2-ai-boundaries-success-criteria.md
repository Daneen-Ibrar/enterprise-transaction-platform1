# Document #2: AI Boundaries & Success Criteria

## Purpose

This document defines the mandatory rules ChatGPT must follow when assisting with development of the Enterprise Transaction Processing Simulator.

These rules are binding for the duration of the project.

The purpose of the project is to demonstrate:

* enterprise-grade authorization,
* transaction lifecycle orchestration,
* auditability,
* operational reliability,
* transactional consistency,
* and disciplined backend engineering practices.

The project is NOT intended to be:

* a generic CRUD application,
* a frontend-heavy demo,
* or a fake banking platform.

---

# ⚠️ GLOBAL ARCHITECTURAL PROHIBITION: NO HARDCODING

## Absolute Rule

The system MUST NOT contain any hardcoded business logic, rules, permissions, roles, thresholds, workflows, or decision-making logic.

This includes but is not limited to:

* hardcoded roles
* hardcoded permissions
* hardcoded approval rules
* hardcoded workflow branches
* hardcoded thresholds (e.g., amounts, limits)
* hardcoded authorization decisions
* hardcoded environment-specific logic

---

## Core Principle

> ALL business logic MUST be externalised and dynamically defined.

All domain behaviour MUST be defined via:

* database-driven configuration
* policy tables
* workflow definitions
* domain models
* rule evaluation services
* or configurable system state

NOT via source code constants or inline logic.

---

## Strict Interpretation Rule

If a requirement can be implemented using hardcoded logic, it is INVALID.

Instead, it MUST be implemented as:

* configurable rule
* persisted policy
* domain-driven evaluation
* or externalized workflow definition

---

## Forbidden Patterns

The following are strictly forbidden:

| Pattern                                               | Status      |
| ----------------------------------------------------- | ----------- |
| `if (role == "ADMIN")`                                | ❌ Forbidden |
| `if (amount > X)` inline business logic               | ❌ Forbidden |
| enum-based permission enforcement                     | ❌ Forbidden |
| static role-permission maps in code                   | ❌ Forbidden |
| workflow branching inside services based on constants | ❌ Forbidden |

---

## Required Patterns

All business decisions MUST be:

* data-driven (database preferred)
* policy-driven (rules stored externally)
* dynamically evaluated in domain services

---

# Part 1: System Definition

The platform is a:

> virtual enterprise transaction processing system

The platform simulates:

* invoice creation
* payment authorization
* transaction settlement
* refunds
* ledger updates
* approval workflows
* reconciliation
* operational recovery

The platform owns:

* transaction lifecycle state
* workflow orchestration
* auditability
* idempotency enforcement
* consistency guarantees

---

## System Constraints

The system must NEVER:

* process real money
* store real card data
* connect to banking rails
* simulate illegal financial activity

All operations are virtual and simulated.

---

# Part 2: Success Criteria (Definition of Done)

The project is complete only when ALL criteria are met.

| #  | Success Criterion                                                                  |
| -- | ---------------------------------------------------------------------------------- |
| 1  | Four database-driven roles exist: Customer, Merchant, Admin, Auditor               |
| 2  | Role permissions are enforced server-side and NOT hardcoded                        |
| 3  | Merchant can create invoices                                                       |
| 4  | High-value invoices require configurable approval rules (NOT hardcoded thresholds) |
| 5  | Customers can perform virtual payments                                             |
| 6  | Transaction engine processes deterministically                                     |
| 7  | Duplicate requests do not create duplicate transactions                            |
| 8  | Ledger consistency is preserved                                                    |
| 9  | Audit logs are append-only and tamper-evident                                      |
| 10 | Auditor has read-only access                                                       |
| 11 | Admin can issue refunds via configurable rules                                     |
| 12 | Settlement workflows operate correctly                                             |
| 13 | Failed transactions recover safely                                                 |
| 14 | Structured logs exist                                                              |
| 15 | Integration tests exist                                                            |
| 16 | No secrets are hardcoded                                                           |
| 17 | Docker Compose startup works                                                       |
| 18 | Technical documentation exists                                                     |

---

# Part 3: Mandatory Authorization Rules

Authorization MUST be enforced in backend services.

Frontend restrictions are NEVER sufficient.

---

## Data-Driven Authorization Requirement

ALL of the following MUST be dynamically defined:

* roles
* permissions
* role-permission mappings
* access policies

NONE may be hardcoded in application logic.

---

## Forbidden Actions

| Role     | Forbidden Actions               |
| -------- | ------------------------------- |
| Customer | Accessing other customers’ data |
| Merchant | Issuing refunds or approvals    |
| Auditor  | Any mutation of system state    |
| Any Role | Bypassing workflow rules        |

(All enforcement MUST be policy-driven, not hardcoded.)

---

# Part 4: Transaction Processing Rules

The transaction engine is authoritative.

---

## Integrity Requirements

* deterministic state transitions
* idempotent request handling
* append-only auditability
* transactional consistency
* recoverable failures

---

## Idempotency Rules

Duplicate requests MUST:

* not create duplicate transactions
* not create duplicate ledger entries
* not create duplicate audit entries

Idempotency MUST NOT rely on inline hardcoded checks.

---

## Refund Rules

Refund logic MUST be:

* governed by configurable policy rules
* not hardcoded by role or amount
* fully auditable

---

# Part 5: Audit Logging Requirements

Audit system is a core enterprise component.

---

## Required Audit Events

All critical system events MUST be recorded via configurable event tracking:

* authentication events
* invoice lifecycle events
* transaction lifecycle events
* refunds
* permission denials
* administrative actions
* recovery workflows

---

## Audit Rules

* append-only
* immutable
* hash chained
* tamper-evident
* no modification allowed

---

# Part 6: Ledger Rules

Ledger system MUST:

* remain immutable in history
* remain transactionally consistent
* NOT use inline business rules

All ledger behaviour MUST be implemented via domain services and/or configurable rules.

---

# Part 7: Non-Functional Requirements

| Area            | Requirement                      |
| --------------- | -------------------------------- |
| Security        | RBAC must be data-driven         |
| Reliability     | No state corruption allowed      |
| Consistency     | PostgreSQL is source of truth    |
| Logging         | Structured logs required         |
| Secrets         | No hardcoding allowed            |
| Testing         | Integration tests required       |
| Maintainability | No business logic in controllers |
| Observability   | Fully traceable workflows        |

---

# Part 8: Technical Constraints

## HARD RULE: NO HARDCODED DOMAIN LOGIC

Even inside application code:

* NO hardcoded roles
* NO hardcoded permissions
* NO inline business decision logic
* NO constant-based workflow branching

All logic MUST be externally defined.

---

| Layer         | Technology                |
| ------------- | ------------------------- |
| Java 21       | Allowed                   |
| Spring Boot 3 | Allowed                   |
| PostgreSQL    | Allowed (source of truth) |
| Redis         | Allowed (ephemeral only)  |
| Thymeleaf     | Allowed                   |
| HTMX          | Allowed                   |
| Tailwind CSS  | Allowed                   |
| Docker        | Allowed                   |

---

## Prohibited Technologies

* React
* Angular
* Vue
* Node.js
* Python
* MongoDB
* GraphQL

---

# Part 9: Forbidden Project Drift

The system MUST reject designs involving:

* hardcoded roles or permissions
* inline business rules
* static workflow logic
* constant-based decision trees

All must be replaced with:

> configurable, data-driven domain logic

---

# Part 10: Communication Rules

ChatGPT MUST:

* enforce separation of configuration and logic
* reject hardcoded implementations
* explain why hardcoding is invalid
* enforce enterprise design discipline

ChatGPT MUST NOT:

* suggest inline rule logic
* allow temporary hardcoding
* approve shortcut implementations

---

# Part 11: Core Principles

| Principle        | Rule                    |
| ---------------- | ----------------------- |
| System of Record | PostgreSQL              |
| Authorization    | Data-driven policies    |
| Auditability     | Immutable logs          |
| Integrity        | No mutation of history  |
| Configuration    | Externalized rules only |
| Separation       | No mixed-layer logic    |
| Security         | Backend authoritative   |

---

# Part 12: Document Control

| Field       | Value     |
| ----------- | --------- |
| Document ID | DOC-2   |
| Version     | 4.0       |
| Enforcement | Mandatory |

---

**End of Document #2**
