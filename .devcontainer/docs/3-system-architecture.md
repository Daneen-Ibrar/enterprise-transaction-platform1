# Document #3: AI System Architecture & Component Boundaries

## Purpose

This document defines the high-level architecture of the Enterprise Transaction Processing Simulator.

It establishes:

- architectural style,
- layer responsibilities,
- dependency direction,
- module communication boundaries,
- transaction ownership,
- operational consistency guarantees,
- authority boundaries,
- and system-wide architectural constraints.

This document exists to prevent:

- tightly coupled code,
- infrastructure leakage into business logic,
- controller-heavy orchestration,
- circular dependencies,
- architectural drift,
- broken transactional consistency,
- and unnecessary distributed-system complexity.

This document is the architectural source of truth for system structure and dependency direction.

---

# Part 1: Architectural Style

## Architectural Pattern

The platform follows a:

> **Composable Modular Monolith Architecture**

The system is internally organized into isolated business modules with:

- explicit ownership boundaries,
- interface-based communication,
- replaceable infrastructure integrations,
- strict dependency direction,
- and centralized transactional consistency.

The platform is intentionally deployed as:

> **a single operational application**

to preserve:

- strong consistency,
- centralized audit integrity,
- simplified operations,
- deterministic workflow execution,
- maintainability,
- and operational observability.

---

## Why This Is NOT a Microservice Architecture

Microservices are intentionally out of scope.

The platform does NOT require:

- distributed transaction coordination,
- service discovery,
- internal network communication,
- independent deployment pipelines,
- eventual consistency between services,
- or independent scaling domains.

The platform contains:

- tightly coupled business workflows,
- centralized audit requirements,
- shared transactional boundaries,
- and operationally unified workflows.

A distributed architecture would introduce unnecessary operational complexity without improving architectural quality.

---

## What "Composable" Means

Composable architecture means:

- business modules are internally isolated,
- dependencies are controlled,
- implementations are replaceable,
- infrastructure is abstracted behind interfaces,
- and modules communicate only through controlled contracts.

Examples of replaceable infrastructure concerns include:

| Capability | Replaceable Through Interfaces |
|---|---|
| Cache Provider | Redis implementation |
| Notification Delivery | Email provider |
| Authentication Provider | Internal or external provider |
| Persistence Layer | Repository abstractions |
| Audit Verification Engine | Verification implementation |

---

# Part 2: High-Level Layer Structure

The system is divided into four primary architectural layers.

| Layer | Responsibility |
|---|---|
| Presentation Layer | UI rendering and interaction |
| Application Layer | Request orchestration and transport coordination |
| Domain Layer | Core business workflows and rules |
| Infrastructure Layer | Persistence and infrastructure integrations |

---

## Dependency Direction Rules

Dependencies must always point inward toward the Domain Layer.

The following rules are mandatory:

| Rule | Description |
|---|---|
| Presentation Layer → Application Layer only | UI cannot directly access business or persistence logic |
| Application Layer → Domain Layer only | Controllers delegate to domain workflows |
| Infrastructure Layer implements Domain interfaces | Infrastructure never owns business rules |
| Domain Layer remains infrastructure-independent | Domain logic cannot depend on frameworks or transport concerns |
| Cross-module database access is forbidden | Modules communicate through services only |

---

# Part 3: Layer Responsibilities

---

## 3.1 Presentation Layer

### Responsibilities

- HTML rendering
- Form interaction
- Partial page updates
- Dashboard rendering
- Validation feedback presentation

### Technologies

- Thymeleaf
- HTMX
- Tailwind CSS

### Forbidden Responsibilities

- Business logic
- Authorization decisions
- Database access
- Transaction orchestration
- Audit generation
- Ledger manipulation

---

## 3.2 Application Layer

### Responsibilities

- HTTP request handling
- DTO validation
- Request/response mapping
- Authentication integration
- Delegation to domain services

### Technologies

- Spring Boot Controllers
- DTOs
- Request Mappers
- Security Adapters

### Forbidden Responsibilities

- Business rule ownership
- Persistence ownership
- Transaction lifecycle management
- Audit chain generation
- Ledger consistency ownership

---

## 3.3 Domain Layer

The Domain Layer contains the primary enterprise value of the platform.

### Responsibilities

- Invoice lifecycle rules
- Approval workflows
- Transaction orchestration
- Settlement simulation
- Refund workflows
- Ledger consistency
- Idempotency enforcement
- Authorization enforcement
- Audit generation
- Reconciliation workflows
- Failure recovery coordination

### Forbidden Responsibilities

- HTTP concerns
- Template rendering
- Infrastructure configuration
- Framework delivery concerns
- Transport-specific logic

### Important Rule

Domain services must never contain:

- SQL queries,
- controller logic,
- HTML rendering,
- framework delivery concerns,
- or infrastructure configuration.

---

## 3.4 Infrastructure Layer

### Responsibilities

- Persistent storage
- Cache storage
- Repository implementations
- Infrastructure configuration
- Notification integrations
- Retry persistence
- Operational infrastructure

### Technologies

- PostgreSQL
- Redis
- Spring Data JPA
- Docker

### Forbidden Responsibilities

- Business rule ownership
- Authorization decisions
- Workflow orchestration
- Transaction lifecycle ownership

---

# Part 4: System Modules

The platform is internally divided into isolated business modules.

Detailed ownership boundaries are formally defined in:

> DOC-006: Module & Responsibility Architecture

The core modules include:

| Module | Responsibility |
|--------|----------------|
| Identity & Access Module | Authentication and authorization |
| Invoice Module | Invoice lifecycle (includes approval workflows) |
| Transaction Module | Transaction orchestration (includes refunds and settlement simulation) |
| Ledger Module | Virtual balance consistency |
| Audit Module | Tamper-evident audit history |
| Reconciliation Module | Operational verification |
| Notification Module | Operational notifications |
| Shared Infrastructure Module | Infrastructure adapters and shared technical concerns |

**Important Notes**:

- Administrative workflows (approvals, refunds) are owned by Invoice Module and Transaction Module respectively
- No separate "Administration Module" exists — this prevents ownership overlap
- Settlement is a workflow inside Transaction Module, not a separate module
- Shared Infrastructure Module provides repositories, cache clients, and email integrations

# Part 5: Internal Transaction Engine

The platform contains an internally owned virtual transaction engine.

This aligns with **ADR-009 (Self-Contained Virtual Transaction Engine)** in DOC-004.

The engine simulates:

- transaction authorization,
- settlement,
- refunds,
- reversals,
- reconciliation,
- and transaction lifecycle management.

---

## Important Constraints

The transaction engine does NOT:

- process real money,
- integrate with banks,
- integrate with Stripe,
- connect to payment networks,
- or function as a regulated financial institution.

All transaction processing is internally simulated.

**See DOC-4 ADR-009 for the full decision rationale, including why Stripe and external payment providers were rejected.**


# Part 6: System Authority Boundaries

Clear ownership boundaries are mandatory.

| Authority | Owns |
|---|---|
| PostgreSQL | Persistent business state |
| Transaction Module | Transaction lifecycle state |
| Ledger Module | Virtual balance consistency |
| Audit Module | Audit integrity |
| Backend Services | Authorization decisions |
| Redis | Temporary operational state |

---

# Part 7: Forbidden Direct Connections

The following architectural violations are forbidden.

| Forbidden Connection | Reason |
|---|---|
| Presentation Layer → Database | Bypasses business rules |
| Controllers → Repositories directly | Creates orchestration leakage |
| Templates → Business logic | Violates separation of concerns |
| Domain Layer → Presentation Layer | Domain must remain transport-independent |
| Domain Services → Concrete infrastructure implementations | Violates dependency inversion |
| Controllers → Multiple repositories | Creates controller-heavy architecture |
| Ledger writes outside LedgerService | Breaks balance consistency |
| Audit writes outside AuditService | Breaks audit integrity |

---

# Part 8: Module Communication Rules

Modules communicate only through defined service interfaces.

## Mandatory Rules

- Controllers delegate to services only
- Business workflows pass through domain services
- Ledger mutations occur only through LedgerService
- Audit events are recorded only through AuditService
- Cross-module table access is forbidden
- Shared mutable state between modules is forbidden
- Infrastructure implementations remain hidden behind interfaces

---

# Part 9: Request Flow Architecture

## Example: Customer Pays an Invoice

| Step | Layer | Action |
|---|---|---|
| 1 | Presentation | User submits payment request |
| 2 | Application | Controller validates request |
| 3 | Application | Authentication context resolved |
| 4 | Domain | Authorization validated |
| 5 | Domain | Idempotency verified |
| 6 | Domain | Transaction workflow initiated |
| 7 | Domain | Ledger consistency checks executed |
| 8 | Domain | Settlement simulated |
| 9 | Domain | Audit event generated |
| 10 | Infrastructure | Persistent state stored |
| 11 | Application | Response mapped |
| 12 | Presentation | HTMX updates UI |

---

## Architectural Rule

Requests must never bypass layers.

Controllers must never manipulate persistence state directly.

---

# Part 10: Transaction Boundaries

The system defines explicit transactional ownership.

| Operation | Strategy |
|---|---|
| Invoice creation | Atomic database transaction |
| Approval workflow | Atomic transaction |
| Transaction initiation | Atomic transactional workflow |
| Settlement update | Atomic ledger transaction |
| Refund workflow | Atomic transactional workflow |
| Audit logging | Same transaction where possible |
| Notifications | Eventually consistent |
| Retry handling | Eventually consistent |

---

# Part 11: Synchronous vs Asynchronous Processing

| Operation | Processing Type |
|---|---|
| Authentication | Synchronous |
| Invoice creation | Synchronous |
| Transaction initiation | Synchronous |
| Refund execution | Synchronous |
| Ledger updates | Synchronous |
| Notification delivery | Asynchronous |
| Retry handling | Asynchronous |
| Audit verification jobs | Asynchronous |
| Reconciliation jobs | Asynchronous |

---

# Part 12: Security Architecture

## Mandatory Security Rules

- Backend authorization is authoritative
- Passwords must be securely hashed
- CSRF protection remains enabled where applicable
- Audit records remain append-only
- Permission checks occur before workflow execution
- Sensitive operations remain fully traceable

---

# Part 13: Reliability Architecture

The platform must remain operationally reliable during failures.

## Reliability Requirements

- Duplicate requests must be safely handled
- Failed transactions must not corrupt state
- Ledger consistency must remain preserved
- Retryable failures must be recoverable
- Notification failures must not fail workflows
- Audit integrity must remain verifiable
- Settlement failures must remain recoverable

---

# Part 14: Observability & Operations

## Operational Requirements

- Structured JSON logging required
- Correlation IDs required
- Security-sensitive actions must remain traceable
- Health-check endpoints required
- Operational failures must remain diagnosable from logs

---

# Part 15: Codespaces & Development Environment Constraints

This section defines development-environment assumptions for GitHub Codespaces.

These constraints do NOT change architectural rules.

They exist to ensure the project remains realistically executable within a constrained cloud development environment.

---

## Development Environment Assumptions

| Concern | Constraint |
|---|---|
| Runtime Model | Single Spring Boot application |
| Infrastructure | Docker Compose |
| Database | PostgreSQL container |
| Cache | Redis container |
| Development Environment | GitHub Codespaces |
| Startup Model | Containerized local development |

---

## Environment Principles

The platform intentionally avoids:

- multi-service orchestration,
- Kubernetes,
- distributed deployment,
- service mesh infrastructure,
- and operationally expensive runtime environments.

The project must remain:

- reviewer-friendly,
- reproducible,
- operationally lightweight,
- and executable within standard GitHub Codespaces limits.

---

## Operational Guidance Reference

**Detailed operational constraints, including:**

- resource limits,
- storage persistence,
- networking,
- startup behaviour,
- workflow friction,
- and reviewer guidance

are documented in DOC-12A (Runtime Topology) and DOC-12B (Startup Sequencing).

See DOC-12A and DOC-12B for step-by-step Codespaces setup, common issues, and recovery procedures.
---

# Part 16: Document Control

| Field | Value |
|---|---|
| Document ID | DOC-3 |
| Version | 3.0 |
| Applies To | ChatGPT |
| Enforcement | Must be referenced before architectural or implementation decisions |

---

**End of Document #3**