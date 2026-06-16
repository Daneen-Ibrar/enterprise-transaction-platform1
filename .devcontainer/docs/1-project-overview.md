# Document #1: Project Overview

## Purpose

This document defines the project ChatGPT is assisting with throughout development.

It establishes:

- project scope,
- architectural intent,
- enterprise priorities,
- technical boundaries,
- user roles,
- operational goals,
- and system responsibilities.

Suggestions that violate these boundaries must be rejected unless explicitly overridden.

This is a project definition document, not a technical tutorial.

---

# Part 1: Project Identity

| Field | Value |
|---|---|
| Project Name | Enterprise Transaction Orchestration & Audit Platform |
| Project Type | Enterprise-grade virtual transaction processing and workflow platform |
| Build Partner | ChatGPT |
| Estimated Timeline | Multi-phase long-term project |

---

## One-Sentence Definition

> A secure, self-contained, role-driven enterprise transaction orchestration platform that simulates payment workflows, approvals, settlement states, refunds, reconciliation, and auditability through an append-only tamper-evident architecture — with no external payment dependencies.

---

# Part 2: Educational & Architectural Scope

This platform is an enterprise architecture and transaction workflow simulation project.

It demonstrates:

- transaction orchestration,
- workflow management,
- authorization,
- auditability,
- reliability engineering,
- operational recovery,
- reconciliation,
- and transaction consistency.

The platform does NOT:

- connect to real banking infrastructure,
- move real money,
- process real payment cards,
- communicate with payment networks,
- or provide legally compliant financial services.

**Payment Integration Decision**: The platform implements its own internal virtual transaction engine instead of integrating with external payment providers like Stripe. This demonstrates complete ownership of transaction orchestration, state management, idempotency, and auditability without external dependencies.

All balances, settlements, refunds, approvals, and transaction flows are internally simulated for educational and architectural demonstration purposes only.

---

# Part 3: The Problem This Solves

Most student payment projects are simplistic CRUD applications that only simulate money movement superficially.

They typically do NOT:

- enforce enterprise authorization,
- model transaction lifecycle state,
- provide operational traceability,
- guarantee idempotency,
- implement audit integrity,
- simulate settlement workflows,
- support reconciliation,
- recover reliably from failures,
- or demonstrate operational consistency.

Real enterprise transaction systems require:

- auditability,
- transactional consistency,
- operational reliability,
- traceable workflows,
- least-privilege authorization,
- deterministic state transitions,
- and failure recovery mechanisms.

Without these protections, organizations face:

- fraud risk,
- operational instability,
- inconsistent transaction state,
- customer disputes,
- regulatory violations,
- and weak forensic visibility.

---

# Part 4: Who Needs This (Stakeholder Pain)

| Stakeholder | Their Pain |
|---|---|
| Banks (Barclays, JP Morgan) | Regulatory exposure due to missing auditability |
| Defence contractors (BAE, Rolls-Royce) | Cannot prove who authorized sensitive operations |
| Telecoms providers (BT) | Customer disputes with weak operational traceability |
| Internal audit teams | Manual log investigation is slow and unreliable |
| Compliance teams | Audit verification is operationally expensive |
| Engineering teams | Failures are difficult to trace and recover |
| Customers | Disputes take too long to resolve |

---

## Core Need

> Organizations need transaction systems where every operational action is traceable, verifiable, tamper-evident, and operationally recoverable.

---

# Part 5: Core Features (Mandatory)

| # | Feature | Description |
|---|---|---|
| 1 | Role-based access control | Four roles: Customer, Merchant, Admin, Auditor |
| 2 | Invoice workflow | Merchants create invoices, Customers pay them |
| 3 | Approval workflow | High-value invoices require Admin approval |
| 4 | Virtual transaction orchestration | Internal simulation of transaction processing |
| 5 | Idempotent transaction handling | Duplicate requests cannot create duplicate transactions |
| 6 | Append-only audit logging | Every security-sensitive action is recorded |
| 7 | Hash-chained audit trail | Tamper-evident cryptographic verification |
| 8 | Auditor verification tooling | Read-only verification of audit integrity |
| 9 | Refund capability | Admin-only refunds with full audit tracking |
| 10 | Settlement-state simulation | Simulated settlement lifecycle transitions |
| 11 | Operational ledger tracking | Consistent virtual transaction state tracking |
| 12 | Notification workflows | Operational notifications and alerts |
| 13 | Failure recovery mechanisms | Retry handling and operational recovery |
| 14 | Reconciliation jobs | Scheduled verification against transaction records |
| 15 | Transaction state machine | Deterministic transaction lifecycle management |
| 16 | Operational observability | Logging, monitoring readiness, traceability |

---

# Part 5.5: Self-Contained Transaction Engine Decision

The platform implements a **completely self-contained virtual transaction engine**.

| Decision | Rationale |
|----------|-----------|
| No external payment providers (Stripe, PayPal, Adyen) | Demonstrates complete ownership of transaction state, idempotency, and audit logic without vendor coupling |
| No real payment APIs | Keeps scope controlled, legally safe, and reproducible on any machine without API keys |
| All transaction state internally managed | Proves you can build transaction orchestration from first principles, not just wrap existing APIs |

---

# Part 6: Virtual Transaction Model

## The Platform Owns

- transaction orchestration,
- authorization workflows,
- approval workflows,
- transaction lifecycle management,
- settlement-state simulation,
- refund workflows,
- transaction state transitions,
- operational ledger consistency,
- idempotency enforcement,
- auditability,
- reconciliation,
- and operational recovery.

---

## The Platform Does NOT Own

- real money movement,
- real card handling,
- banking infrastructure,
- payment network communication,
- real financial settlement,
- tax accounting,
- financial reporting compliance,
- or banking-grade accounting systems.

---

## Important Scope Clarification

The platform simulates enterprise transaction architecture concepts.

It is NOT:

- a bank,
- a payment processor,
- a payment gateway,
- a core banking system,
- or a legally compliant financial platform.

---

## Virtual Ledger Clarification

The operational ledger exists solely to support:

- workflow consistency,
- transaction simulation,
- reconciliation,
- refunds,
- and audit verification.

It is NOT a real accounting ledger or double-entry bookkeeping system.

---

# Part 7: User Roles

| Role | Permissions |
|---|---|
| Customer | View and pay their own invoices |
| Merchant | Create invoices and view received payments |
| Admin | Approve invoices, issue refunds, manage workflows |
| Auditor | Read-only access to audit logs and verification tooling |

---

## Authorization Rules (Non-Negotiable)

- Backend services are the sole authority for authorization decisions
- Authorization must never rely solely on frontend restrictions
- Auditor users must NEVER modify system state
- Role definitions must be database-driven
- Permission enforcement must occur before business execution
- Unauthorized operations must be rejected server-side

---

# Part 8: Enterprise Values Demonstrated

| Value | Demonstration |
|---|---|
| Security | RBAC, least privilege, backend authorization |
| Compliance | Append-only audit logs, tamper detection |
| Reliability | Idempotency, retries, transactional consistency |
| Operational Thinking | Logging, monitoring, recovery workflows |
| Maintainability | Modular architecture, clean boundaries |
| Auditability | Traceable transaction lifecycle |
| Consistency | Deterministic state management |
| Recoverability | Failure handling and reconciliation |

---

# Part 9: Architectural Philosophy

The project prioritizes:

- correctness over feature count,
- security over convenience,
- auditability over implementation speed,
- reliability over visual complexity,
- maintainability over abstraction purity,
- and operational simplicity over distributed complexity.

---

## Architecture Style

> Composable Modular Architecture

The system is designed as:

- a single deployable application,
- with internally isolated modules,
- interface-driven communication,
- controlled dependency direction,
- and replaceable infrastructure integrations.

---

## Why Microservices Are Out of Scope

Microservices are intentionally avoided because the platform does not require:

- independent service deployment,
- distributed databases,
- service discovery,
- network-based internal communication,
- distributed transaction coordination,
- or independent scaling domains.

Premature distributed-system complexity would increase:

- debugging difficulty,
- operational overhead,
- deployment complexity,
- and consistency risks.

without meaningful architectural benefit.

---

# Part 10: System Authority Boundaries

| Authority | Owns |
|---|---|
| PostgreSQL | Internal business state (source of truth) |
| Transaction Module | Transaction lifecycle state |
| Ledger Module | Operational transaction consistency |
| Backend Services | Authorization and workflow decisions |
| Audit Module | Audit integrity and verification |
| Redis | Temporary operational state (idempotency keys, cache) |

---

# Part 11: Forbidden Project Drift

| Forbidden Direction | Reason |
|---|---|
| CRUD demo | No enterprise engineering value |
| Toy app | Does not demonstrate professional capability |
| Pet project | Not serious enough for enterprise interviews |
| Hardcoded demo | Violates maintainability principles |
| Frontend-heavy SPA rewrite | Violates architecture constraints |
| Unnecessary microservice sprawl | Adds operational complexity without value |
| Real-world payment processor | Requires banking, regulatory, and PCI compliance |
| Real banking integrations | Outside educational scope |

---

# Part 12: Out of Scope

| Excluded Feature | Reason |
|---|---|
| Real-money processing | Platform is fully simulated |
| Card data storage | PCI compliance risk |
| Banking integrations | Out of scope |
| Payment network integrations | Not required for architecture demonstration |
| Multi-currency support | GBP-only keeps scope controlled |
| Subscription billing | Significantly increases complexity |
| OAuth / social login | Email/password sufficient |
| Machine-learning fraud detection | Research-scale complexity |
| Production cloud infrastructure | Not the primary architectural focus |
| Real accounting compliance | Outside platform purpose |

---

## Part 13: Technical Constraints

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 |
| Database | PostgreSQL |
| Cache | Redis |
| Frontend | Thymeleaf + HTMX + Tailwind CSS |
| Containerization | Docker + Docker Compose |
| **Development Environment** | **GitHub Codespaces** |

## Development Environment: GitHub Codespaces

- All development occurs within GitHub Codespaces
- Environment is cloud-based, browser-accessible, and reproducible
- No local setup required beyond a browser and GitHub account
- Dev container configuration (`.devcontainer/devcontainer.json`) defines:
  - Java 21, Maven, Docker-in-Docker
  - PostgreSQL and Redis services
  - VS Code extensions for Java, Spring Boot, Thymeleaf
- Environment is fully pre-configured to run `docker-compose up` immediately after creation
- This ensures perfect reproducibility for reviewers and eliminates "works on my machine" issues

---

## Prohibited Technologies (Unless Explicitly Requested)

- React
- Angular
- Vue
- Node.js
- Python
- MongoDB
- GraphQL

---

# Part 14: Deployment Philosophy

The platform must:

- start reliably through Docker Compose,
- support reproducible local environments,
- maintain deterministic startup behavior,
- remain operationally observable,
- and support clean environment recreation.

Operational simplicity is prioritized over cloud-native complexity.

---

# Part 15: Success Criteria

The platform is considered complete only when:

- all user roles function correctly,
- authorization is enforced server-side,
- transaction state transitions are deterministic,
- duplicate requests are safely handled,
- audit logs are tamper-evident,
- reconciliation processes function correctly,
- operational failures are recoverable,
- and the system starts successfully through documented Docker Compose workflows.

---

# Part 16: Document Control

| Field | Value |
|---|---|
| Document ID | DOC-1 |
| Version | 2.1 |
| Applies To | ChatGPT |
| Enforcement | Must be referenced before architectural or implementation decisions |

---

**End of Document #1**