# Document #6: Module & Responsibility Architecture

## Purpose

This document defines the internal architectural structure of the platform.

It bridges:

* DOC-1 (Project Overview) → WHAT the platform is
* DOC-4 (Architectural Decision Records) → WHY architectural choices were made
* DOC-5 (Domain Model & Lifecycle) → WHAT business guarantees exist

This document defines:

* module boundaries,
* ownership responsibilities,
* dependency direction,
* orchestration responsibilities,
* transactional boundaries,
* and communication rules.

The goal is to ensure the platform remains:

* modular,
* maintainable,
* testable,
* operationally predictable,
* and aligned with enterprise architectural principles.

This document intentionally avoids:

* implementation details,
* framework annotations,
* database schemas,
* endpoint definitions,
* infrastructure deployment configuration,
* or low-level code structure.

This is an architectural responsibility document — not an implementation tutorial.

---

# Part 1: Architectural Principles

The module architecture follows the principles below.

| Principle                  | Meaning                                                                   |
| -------------------------- | ------------------------------------------------------------------------- |
| Clear ownership boundaries | Each module owns a specific business capability                           |
| Controlled dependencies    | Modules communicate only through approved interfaces                      |
| Separation of concerns     | Business logic, infrastructure, and delivery concerns remain isolated     |
| Replaceable infrastructure | Infrastructure implementations can change without changing business logic |
| Transactional consistency  | Critical workflows execute atomically where required                      |
| Auditability by design     | Important actions remain traceable across modules                         |
| Operational simplicity     | Architecture prioritizes maintainability over distributed complexity      |
| Explicit orchestration     | One module coordinates a workflow at a time                               |

---

# Part 2: High-Level Module Structure

## Core Modules

| Module                       | Responsibility                                               |
| ---------------------------- | ------------------------------------------------------------ |
| Identity & Access Module     | Authentication, authorization, user roles                    |
| Invoice Module               | Invoice lifecycle and approval requirements                  |
| Transaction Module           | Transaction orchestration, refunds, and settlement workflows |
| Ledger Module                | Operational balance tracking and consistency                 |
| Audit Module                 | Immutable audit history and verification                     |
| Reconciliation Module        | Consistency verification and mismatch detection              |
| Notification Module          | Asynchronous operational notifications                       |
| Shared Infrastructure Module | Infrastructure adapters and shared technical concerns        |

---

## Important Architectural Notes

* No separate Administration Module exists
* Approval workflows are owned by Invoice Module
* Refund workflows are owned by Transaction Module
* Settlement simulation is part of Transaction Module
* Shared Infrastructure Module is strictly technical and never owns domain business logic or workflow orchestration

This structure aligns with DOC-003 and DOC-005 ownership boundaries.

---

# Part 3: Architectural Layering

## Layered Dependency Model

```text
Presentation Layer
(Thymeleaf + HTMX)

        ↓

Application Layer
(Controllers, DTOs, Security)

        ↓

Domain Modules
(Business workflows and orchestration)

        ↓

Infrastructure Layer
(PostgreSQL, Redis, Email, Persistence)
```

---

## Dependency Rules

| Rule                  | Requirement                                                |
| --------------------- | ---------------------------------------------------------- |
| Presentation layer    | Cannot contain business logic                              |
| Application layer     | Coordinates requests and authorization only                |
| Domain modules        | Own business rules and workflows                           |
| Infrastructure layer  | Implements technical integrations only                     |
| Domain modules        | Must not depend directly on infrastructure implementations |
| Circular dependencies | Forbidden                                                  |
| Cross-module access   | Allowed only through approved interfaces                   |

---

# Part 4: Module Responsibilities

---

## 4.1 Identity & Access Module

### Owns

* User authentication
* Role assignment
* Permission evaluation
* Session identity
* Authorization enforcement

### Does NOT Own

* Transaction workflows
* Invoice processing
* Audit persistence
* Business orchestration

### Architectural Responsibilities

* Ensure backend authorization is authoritative
* Enforce least-privilege access
* Prevent unauthorized workflow execution

### Core Guarantees

| Guarantee                                                     |
| ------------------------------------------------------------- |
| Authorization is enforced server-side                         |
| Roles are centrally managed                                   |
| Authentication logic remains isolated from business workflows |

---

## 4.2 Invoice Module

### Owns

* Invoice lifecycle
* Approval requirements
* Invoice status transitions
* Invoice validation rules
* Approval policy rules (`approval_rule` table)

### Does NOT Own

* Payment execution
* Ledger consistency
* Audit persistence implementation

### Architectural Responsibilities

* Determine whether invoices are payable
* Enforce approval requirements
* Prevent invalid invoice state transitions


### Core Guarantees

| Guarantee                                        |
| ------------------------------------------------ |
| Closed invoices cannot be paid again             |
| Approval requirements are enforced consistently  |
| Invoice workflows remain independently auditable |

---

## 4.3 Transaction Module

### Owns

* Transaction orchestration
* Transaction lifecycle state
* Idempotency coordination
* Refund workflows
* Settlement simulation
* Refund policy rules (`refund_rule` table)

### Does NOT Own

* User authentication
* Invoice ownership rules
* Audit persistence implementation
* Infrastructure caching

### Architectural Responsibilities

* Coordinate transaction execution
* Enforce deterministic transaction transitions
* Prevent duplicate successful outcomes
* Coordinate workflow rollback on failure

### Core Guarantees

| Guarantee                                                      |
| -------------------------------------------------------------- |
| Duplicate requests do not create duplicate successful outcomes |
| Transaction transitions remain valid and deterministic         |
| Transaction execution remains internally consistent            |

---

## 4.4 Ledger Module

### Owns

* Operational balance records
* Ledger entries
* Balance consistency verification

### Does NOT Own

* Transaction orchestration
* Invoice approval workflows
* Authentication logic

### Architectural Responsibilities

* Maintain operational balance consistency
* Provide immutable balance movement history
* Support reconciliation verification

### Core Guarantees

| Guarantee                             |
| ------------------------------------- |
| Ledger entries are append-only        |
| Balance history remains traceable     |
| Ledger consistency remains verifiable |

---

## 4.5 Audit Module

### Owns

* Immutable audit history
* Hash-chain verification
* Tamper-evidence verification
* Audit retrieval

### Does NOT Own

* Business workflows
* Authorization logic
* Transaction orchestration

### Architectural Responsibilities

* Record security-sensitive activity
* Preserve historical integrity
* Detect historical tampering attempts

### Core Guarantees

| Guarantee                                               |
| ------------------------------------------------------- |
| Audit history is append-only                            |
| Historical tampering attempts are detectable            |
| Audit visibility does not grant modification capability |

---

## 4.6 Reconciliation Module

### Owns

* Operational consistency verification
* Mismatch detection
* Verification reporting
* Reconciliation history

### Does NOT Own

* Transaction execution
* Ledger mutation
* Workflow orchestration

### Architectural Responsibilities

* Independently verify consistency
* Detect operational drift
* Support investigation workflows

### Core Guarantees

| Guarantee                                                      |
| -------------------------------------------------------------- |
| Reconciliation operates independently from execution workflows |
| Verification history remains immutable                         |
| Mismatches are detectable and traceable                        |

---

## 4.7 Notification Module

### Owns

* Notification delivery
* Notification retry handling
* Notification templates
* Delivery tracking

### Does NOT Own

* Business workflow decisions
* Transaction state management
* Authorization logic

### Architectural Responsibilities

* Deliver operational notifications asynchronously
* Prevent notification failures from blocking transactions

### Core Guarantees

| Guarantee                                                       |
| --------------------------------------------------------------- |
| Notifications are operationally secondary                       |
| Notification failures do not break transaction consistency      |
| Notification retries remain isolated from transaction workflows |

---

## 4.8 Shared Infrastructure Module

### Owns

* Persistence implementations
* Redis integrations
* Email integrations
* Shared technical configuration
* Infrastructure adapters

### Does NOT Own

* Business rules
* Workflow orchestration
* Domain decision-making

### Architectural Responsibilities

* Provide replaceable infrastructure implementations
* Isolate infrastructure concerns from domain workflows

### Core Guarantees

| Guarantee                                                                 |
| ------------------------------------------------------------------------- |
| Infrastructure remains replaceable                                        |
| Business logic does not depend on concrete infrastructure implementations |
| Technical integrations remain isolated from domain workflows              |

---

# Part 5: Communication Rules

## Allowed Communication

| Pattern                                         | Allowed |
| ----------------------------------------------- | ------- |
| Module → exported interface                     | Yes     |
| Synchronous orchestration                       | Yes     |
| Asynchronous notifications                      | Yes     |
| Infrastructure implementation behind interfaces | Yes     |

---

## Forbidden Communication

| Forbidden Dependency                                       | Reason                                 |
| ---------------------------------------------------------- | -------------------------------------- |
| Direct database access across modules                      | Violates ownership boundaries          |
| Circular dependencies                                      | Creates instability and tight coupling |
| Domain logic inside controllers                            | Violates separation of concerns        |
| Domain modules depending on infrastructure implementations | Reduces replaceability                 |
| Reconciliation coupled to live transaction execution       | Violates operational isolation         |

---

# Part 6: Workflow Orchestration Principles

The architecture enforces explicit orchestration ownership.

| Workflow                    | Orchestrating Module  |
| --------------------------- | --------------------- |
| Invoice approval workflow   | Invoice Module        |
| Payment execution workflow  | Transaction Module    |
| Refund workflow             | Transaction Module    |
| Reconciliation workflow     | Reconciliation Module |
| Audit verification workflow | Audit Module          |

---

## Orchestration Rules

| Rule                       | Meaning                                                    |
| -------------------------- | ---------------------------------------------------------- |
| Single orchestrator        | One module coordinates a workflow                          |
| Delegated responsibilities | Supporting modules provide capabilities, not orchestration |
| Workflow consistency       | Orchestrators enforce valid sequencing                     |
| Audit visibility           | Important orchestration actions remain traceable           |

---

# Part 7: Transactional Boundaries

## Transaction Principles

| Principle                    | Meaning                                                     |
| ---------------------------- | ----------------------------------------------------------- |
| Atomic execution             | Critical state changes succeed together or fail together    |
| Consistent rollback          | Partial workflow corruption must be prevented               |
| Audit traceability           | Important failures remain historically visible              |
| Deterministic outcomes       | Same valid input produces predictable workflow behaviour    |
| Non-observable partial state | Partial state mutation must never become externally visible |

---

## Example: Payment Workflow Boundary

The payment workflow conceptually includes:

* idempotency validation,
* invoice validation,
* transaction state creation,
* ledger update,
* and audit generation.

If a critical operation fails before completion:

* the workflow fails consistently,
* invalid partial state is prevented,
* partial mutation does not become externally observable,
* and operational history remains auditable.

---

# Part 8: Architectural Constraints

The platform intentionally avoids:

| Excluded Direction                   | Reason                             |
| ------------------------------------ | ---------------------------------- |
| Microservice decomposition           | Unnecessary operational complexity |
| Shared mutable state between modules | Violates ownership boundaries      |
| Infrastructure-aware business logic  | Reduces maintainability            |
| Frontend-driven authorization        | Security risk                      |
| Distributed transaction coordination | Outside project scope              |

---

# Part 9: Conceptual Module Organisation

```text
.devcontainer/
└── devcontainer.json

src/main/java/com/enterprise/
├── identity/
├── invoice/
├── transaction/
├── ledger/
├── audit/
├── reconciliation/
├── notification/
└── shared/
```

Internal implementation details may evolve provided architectural boundaries remain intact.

---

# Part 10: Alignment with Previous Documents

| Document | Relationship                                                        |
| -------- | ------------------------------------------------------------------- |
| DOC-1  | Defines overall project scope and priorities                        |
| DOC-3  | Defines system-wide architectural layering and dependency direction |
| DOC-4  | Explains why architectural decisions were made                      |
| DOC-5  | Defines business entities, workflows, and invariants                |

---

# Part 11: Document Control

| Field       | Value                              |
| ----------- | ---------------------------------- |
| Document ID | DOC-6                            |
| Version     | 4.0                                |
| Applies To  | ChatGPT                            |
| Depends On  | DOC-1, DOC-3, DOC-4, DOC-5 |

---

**End of Document #6**
