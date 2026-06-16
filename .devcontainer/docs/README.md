# Enterprise Transaction Orchestration & Audit Platform

## One-Sentence Summary

> A policy-driven virtual transaction engine with immutable audit trails, idempotent processing, reconciliation workflows, and enterprise-grade operational reliability — implemented as a modular monolith with no external payment providers.

---

# Project Overview

This platform is a self-contained enterprise transaction orchestration system that simulates:

* invoice workflows,
* approvals,
* virtual transaction processing,
* settlement states,
* refunds,
* reconciliation,
* audit verification,
* and operational recovery.

The project focuses on demonstrating:

* enterprise backend architecture,
* transactional consistency,
* auditability,
* authorization,
* reliability engineering,
* and operational correctness.

The platform does **NOT**:

* process real money,
* integrate with Stripe or banking systems,
* store payment cards,
* or operate as a regulated financial platform.

For full project scope and architectural intent, see:

* [DOC-1: Project Overview](./docs/01-project-overview.md)

---

# Enterprise Engineering Constraints

## 1. No Hardcoded Business Logic

The platform strictly forbids hardcoded domain logic.

The following patterns are prohibited:

* `if (role == "ADMIN")`
* inline authorization decisions
* hardcoded approval thresholds
* static workflow branching
* fixed retry policies
* embedded permission mappings

All business behaviour must be:

* policy-driven,
* database-driven,
* externally configured,
* and dynamically evaluated at runtime.

---

## 2. Self-Contained Transaction Engine

The platform uses an internally owned virtual transaction engine only.

The system does NOT integrate with:

* Stripe,
* PayPal,
* Adyen,
* banking APIs,
* or payment gateways.

All transaction processing is simulated internally.

---

## 3. Modular Monolith Architecture

The system is intentionally designed as a modular monolith.

Architecture constraints:

* single Spring Boot application,
* centralized transactional consistency,
* no Kubernetes,
* no service discovery,
* no distributed transactions,
* no microservice decomposition.

---

## 4. Frontend Constraints

Frontend stack:

* Thymeleaf
* HTMX
* Tailwind CSS

The project intentionally avoids:

* React,
* Angular,
* Vue,
* or SPA architecture.

---

## 5. Configuration Rules

Implementation examples throughout the project are illustrative only.

Values such as:

* ports,
* hostnames,
* TTL values,
* retry limits,
* and environment variables

must remain externally configurable and must never be treated as architectural constants.

---

# AI Assistant Constraints

Any AI assistant contributing to this project must follow these rules:

| Rule                           | Requirement                                    |
| ------------------------------ | ---------------------------------------------- |
| No workarounds                 | Do not suggest temporary hardcoding            |
| No fictional features          | Do not invent undocumented behaviour           |
| Respect architecture documents | Follow DOC-001 to DOC-015                      |
| No speculative technologies    | Do not introduce unsupported stacks            |
| No environment assumptions     | Do not assume fixed topology or infrastructure |
| Flag missing information       | Explicitly identify undocumented gaps          |
| Preserve boundaries            | Maintain module and layer ownership rules      |

---

# Quick Start

## Prerequisites

Recommended:

* GitHub Codespaces

Alternative:

* Docker Desktop
* Docker Compose

---

## Run Locally

```bash
git clone https://github.com/yourusername/transaction-platform.git
cd transaction-platform
docker-compose up
```

---

## Application Access

```text
http://localhost:8080
```

---

# Development Credentials

No credentials are hardcoded in source control.

Local development credentials are injected through:

* environment variables,
* Docker secrets,
* CI/CD secret injection,
* or migration seed configuration.

Example local `.env` configuration:

```env
TEST_CUSTOMER_EMAIL=customer@test.com
TEST_CUSTOMER_PASSWORD=changeme

TEST_MERCHANT_EMAIL=merchant@test.com
TEST_MERCHANT_PASSWORD=changeme

TEST_ADMIN_EMAIL=admin@test.com
TEST_ADMIN_PASSWORD=changeme

TEST_AUDITOR_EMAIL=auditor@test.com
TEST_AUDITOR_PASSWORD=changeme
```

These values are examples only and must never be committed as real credentials.

For startup sequencing and environment initialization, see:

* DOC-12B: Startup Sequencing
* DOC-09A: Identity and RBAC

---

# Core Enterprise Features

* Role-based access control
* Policy-driven authorization
* Idempotent transaction processing
* Immutable audit history
* Hash-chained audit verification
* Append-only persistence model
* Settlement-state simulation
* Refund workflows
* Reconciliation verification
* Failure recovery coordination
* Structured operational logging
* Correlation ID tracing
* Retry and recovery workflows

---

# Technology Stack

| Layer                   | Technology                      |
| ----------------------- | ------------------------------- |
| Backend                 | Java 21 + Spring Boot 3         |
| Database                | PostgreSQL 16                   |
| Cache                   | Redis 7                         |
| Frontend                | Thymeleaf + HTMX + Tailwind CSS |
| Infrastructure          | Docker + Docker Compose         |
| Development Environment | GitHub Codespaces               |

---

# Architecture Overview

```text
┌─────────────────────────────────────────────────────────────┐
│ PRESENTATION LAYER (Thymeleaf + HTMX + Tailwind CSS)       │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ APPLICATION LAYER (Controllers, DTOs, Security)            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ DOMAIN LAYER (Transactions, Ledger, Audit, Reconciliation) │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│ INFRASTRUCTURE LAYER (PostgreSQL, Redis, External IO)      │
└─────────────────────────────────────────────────────────────┘
```

---

# Core Architectural Principles

* PostgreSQL is the authoritative system of record
* Redis is ephemeral operational infrastructure only
* Audit history is append-only and tamper-evident
* Authorization is backend-authoritative
* Domain workflows remain deterministic
* Infrastructure remains replaceable
* Transaction workflows preserve strong consistency
* Reconciliation remains operationally isolated

---

# Document Suite

## PLEASE CONSULT THE MILESTONE.md DOCUMENT EACH STEP OF THE WAY


## Vision & Governance

| Document | Description                      |
| -------- | -------------------------------- |
| DOC-1  | Project Overview                 |
| DOC-2  | AI Boundaries & Success Criteria |

---

## Architecture

| Document | Description                          |
| -------- | ------------------------------------ |
| DOC-3  | System Architecture                  |
| DOC-4  | Architectural Decision Records       |
| DOC-5  | Domain Model & Lifecycle             |
| DOC-6  | Module & Responsibility Architecture |

---

## Data & Persistence

| Document | Description                          |
| -------- | ------------------------------------ |
| DOC-7  | Persistence & Ownership Architecture |

---

## API & Application

| Document | Description                          |
| -------- | ------------------------------------ |
| DOC-8A | Controllers & Transport Architecture |
| DOC-8B | Transaction Behaviour Specification  |

---

## Security

| Document | Description                         |
| -------- | ----------------------------------- |
| DOC-9A | Identity & RBAC Architecture        |
| DOC-9B | Threat Model & Security Constraints |

---

## Reliability

| Document | Description                  |
| -------- | ---------------------------- |
| DOC-10A | Policy Engine Architecture   |
| DOC-10B | Failure & Recovery Workflows |

---

## Testing

| Document | Description          |
| -------- | -------------------- |
| DOC-11A | Testing Strategy     |
| DOC-11B | Verification Domains |

---

## Deployment

| Document | Description                          |
| -------- | ------------------------------------ |
| DOC-12A | Runtime Topology                     |
| DOC-12B | Startup Sequencing                   |
| DOC-13  | Network & Infrastructure Constraints |

---

## Scalability

| Document | Description               |
| -------- | ------------------------- |
| DOC-014  | Evolution & Scaling Notes |

---

## API Contracts

| Document | Description                     |
| -------- | ------------------------------- |
| DOC-015  | Endpoint Contract Specification |

---

# Intended Audience

This project is designed for:

* technical reviewers,
* apprenticeship assessors,
* backend engineering interviews,
* and enterprise architecture evaluation.

The project intentionally prioritizes:

* correctness,
* maintainability,
* auditability,
* operational thinking,
* and architectural discipline

over feature count or frontend complexity.

---

**End of README**