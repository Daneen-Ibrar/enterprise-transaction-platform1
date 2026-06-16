# Document #4: Architectural Decision Records (ADR)

## Purpose

This document is a collection of architectural decision records explaining:

- WHY major engineering decisions were made,
- WHAT alternatives were rejected,
- and WHAT tradeoffs were accepted.

The purpose of this document is to:

- demonstrate engineering judgement,
- prevent architectural drift,
- justify implementation direction,
- support interview discussion,
- and preserve long-term architectural consistency.

This document is not a tutorial.

It is a formal engineering decision log.

---

# ADR Structure

Every ADR follows the same structure.

| Field | Description |
|---|---|
| Decision | What was chosen |
| Context | What problem required a decision |
| Alternatives Rejected | What was not chosen and why |
| Consequences | Positive and negative outcomes of the decision |

---

# ADR-001: Why composable modular architecture instead of microservices

| Field | Content |
|---|---|
| Decision | The system uses a composable modular architecture deployed as a single operational application |
| Context | The platform requires strong transactional consistency, centralized auditability, simplified operations, and controlled modular boundaries |
| Alternatives Rejected | Microservices. Rejected because: introduces distributed transactions, service discovery, network failures, operational overhead, eventual consistency problems, and significantly harder debugging |
| Consequences | The system is easier to operate, debug, test, and maintain. Independent service scaling is not supported, but this is acceptable for the project scope |

---

# ADR-002: Why PostgreSQL is the system of record

| Field | Content |
|---|---|
| Decision | PostgreSQL is authoritative for all persistent business state |
| Context | The system requires ACID transactions, relational integrity, auditability, and strong consistency guarantees |
| Alternatives Rejected | MongoDB (weaker transactional guarantees), Redis persistence (not durable enough), DynamoDB (operational complexity and vendor coupling) |
| Consequences | Schema evolution must be managed carefully through migrations. Strong consistency and transactional integrity are preserved |

---

# ADR-003: Why Redis is not authoritative

| Field | Content |
|---|---|
| Decision | Redis is used only for ephemeral operational state such as idempotency keys and temporary coordination |
| Context | Fast operational lookups are required without compromising consistency guarantees |
| Alternatives Rejected | Using Redis as a primary datastore. Rejected because: data durability and transactional guarantees are insufficient for financial workflows |
| Consequences | Redis failures do not compromise authoritative business state. PostgreSQL remains the source of truth |

---

# ADR-004: Why audit logs are append-only

| Field | Content |
|---|---|
| Decision | Audit records are immutable and append-only |
| Context | Enterprise systems require forensic traceability and tamper-evident operational history |
| Alternatives Rejected | Mutable audit logs, soft deletes, or overwriting historical events. Rejected because: historical integrity becomes unverifiable |
| Consequences | Incorrect actions must be corrected through compensating audit events rather than modification |

---

# ADR-005: Why transaction workflows remain synchronous

| Field | Content |
|---|---|
| Decision | Core transaction execution workflows occur synchronously within the request lifecycle |
| Context | Users require immediate workflow feedback and transactional consistency during execution |
| Alternatives Rejected | Fully asynchronous transaction execution through message queues. Rejected because: introduces operational complexity and eventual consistency concerns |
| Consequences | Request latency may increase during heavy processing, but consistency and simplicity are preserved |

---

# ADR-006: Why notifications are asynchronous

| Field | Content |
|---|---|
| Decision | Notifications are processed asynchronously outside critical transaction workflows |
| Context | Notification delivery is operationally secondary to transaction consistency |
| Alternatives Rejected | Synchronous notification delivery. Rejected because: notification failures could block transaction execution |
| Consequences | Notifications may be delayed slightly, but transaction workflows remain resilient |

---

# ADR-007: Why backend authorization is authoritative

| Field | Content |
|---|---|
| Decision | Authorization enforcement occurs entirely in backend services |
| Context | Frontend restrictions alone cannot enforce security boundaries |
| Alternatives Rejected | Frontend-only authorization checks. Rejected because: client-side enforcement is bypassable |
| Consequences | All protected operations require server-side permission verification |

---

---

# ADR-008: Why Java Spring Boot was chosen for the transaction platform

| Field | Content |
|---|---|
| Decision | The platform is implemented using Java 21 and Spring Boot 3 |
| Context | The project aims to simulate the internal architecture of an enterprise payment gateway and transaction-processing platform. The primary goals are transactional consistency, auditability, reliability, maintainability, policy-driven behaviour, reconciliation workflows, and long-term architectural discipline. The technology stack must support complex domain modelling, strong transactional guarantees, modular architecture, and enterprise-grade engineering practices. |
| Alternatives Rejected | **Node.js**: Rejected because dynamic typing increases risk in complex business domains and the ecosystem is more commonly optimized for rapid application delivery than transaction-centric enterprise systems. **Python**: Rejected because the project prioritizes enterprise architecture and transactional correctness over rapid prototyping. Dynamic typing and lower prevalence in transaction-processing platforms make it a weaker fit for the project's objectives. **Go**: Rejected because although it provides excellent performance and concurrency, it requires significantly more custom implementation for enterprise application patterns that Spring Boot provides out of the box. **Rust**: Rejected because the project's goal is enterprise workflow architecture rather than low-level systems engineering. The additional complexity would increase implementation effort without providing proportional architectural value. **C#/.NET**: Rejected despite being a strong enterprise alternative because the project was standardized around the Java ecosystem and Spring Boot's mature support for modular, transaction-oriented enterprise applications. |
| Consequences | The platform benefits from strong type safety, mature transaction management, extensive testing support, a large enterprise ecosystem, and architecture patterns commonly used in financial and transaction-processing systems. Development may require more upfront structure and boilerplate than some alternatives, but long-term maintainability, consistency, and enterprise credibility are significantly improved. The resulting architecture more closely reflects the technologies and engineering practices commonly found in enterprise transaction-processing platforms. |

---

# ADR-009: Why Thymeleaf + HTMX was chosen over SPA frameworks

| Field | Content |
|---|---|
| Decision | The platform uses Thymeleaf with HTMX rather than React, Vue, or Angular |
| Context | The project prioritizes backend architecture, transaction workflows, auditability, and operational engineering |
| Alternatives Rejected | React, Vue, Angular. Rejected because: introduces unnecessary frontend complexity, duplicated state management, and increased architectural overhead |
| Consequences | The frontend is simpler and more maintainable. The project remains focused on enterprise backend engineering |

---

# ADR-010: Why the platform uses a self-contained virtual transaction engine (no Stripe, no real money)

| Field | Content |
|---|---|
| Decision | The platform implements a completely self-contained virtual transaction engine with no external payment providers (Stripe, PayPal, Adyen) and no real money movement |
| Context | The project's primary engineering goal is to design and simulate enterprise transaction infrastructure internally — not outsource transaction orchestration to external providers |
| Alternatives Rejected | **Stripe/PayPal/Adyen**: Rejected because they already provide transaction orchestration, settlement handling, dispute workflows, and transaction state management internally — outsourcing would hide the exact engineering problems this project aims to demonstrate. **Real-money processing**: Rejected because it introduces legal, compliance, PCI, and financial regulatory complexity. |
| Consequences | The platform must implement its own internal transaction lifecycle engine, virtual ledger management, settlement simulation, reconciliation logic, audit workflows, and operational tooling. This significantly increases architectural depth and educational value. The trade-off is that no real money moves — acceptable because the goal is enterprise architecture, not payment processing. |

## What Outsourcing to Stripe Would Hide (And Why Self-Contained Is Better)

Using Stripe would outsource these engineering problems:

- transaction orchestration,
- settlement lifecycle management,
- operational state transitions,
- reconciliation workflows,
- transaction recovery,
- idempotency handling,
- and ledger consistency.

By building a self-contained engine, you demonstrate understanding of **all** these patterns — not just how to call an API.

## Additional Engineering Rationale

Using Stripe or similar providers would outsource many of the exact engineering problems this project is intended to demonstrate, including:

- transaction orchestration,
- settlement lifecycle management,
- operational state transitions,
- reconciliation workflows,
- transaction recovery,
- idempotency handling,
- and ledger consistency.

The project is intentionally focused on:

- enterprise transaction architecture,
- operational correctness,
- auditability,
- reliability,
- reconciliation,
- and financial-style workflow design.

The goal is NOT:
- moving real money,
- integrating external payment APIs,
- consuming existing admin dashboards,
- or building a commercial payment gateway.

Instead, the goal is to understand and implement the internal architecture patterns used inside enterprise financial systems.

---


# ADR-011: Why domain services own business workflows

| Field | Content |
|---|---|
| Decision | Business workflows are owned by domain services |
| Context | Enterprise business rules require centralized ownership and testability |
| Alternatives Rejected | Fat controllers and database-driven workflow logic. Rejected because: creates coupling, duplication, and poor testability |
| Consequences | Controllers remain thin and orchestration logic remains centralized and testable |

---

# ADR-012: Why controllers remain thin

| Field | Content |
|---|---|
| Decision | Controllers handle only HTTP concerns and delegation |
| Context | Separation of concerns improves maintainability and architectural clarity |
| Alternatives Rejected | Controllers containing business logic. Rejected because: creates tightly coupled delivery logic |
| Consequences | Business workflows remain reusable outside HTTP entry points |

---

# ADR-013: Why modular boundaries are enforced through interfaces

| Field | Content |
|---|---|
| Decision | Modules communicate through interfaces defined in the domain layer |
| Context | Infrastructure implementations must remain replaceable and isolated |
| Alternatives Rejected | Direct infrastructure coupling from business logic. Rejected because: reduces replaceability and testability |
| Consequences | Additional abstraction layers are required, but architectural isolation improves maintainability |

---

# ADR-014: Why ledger updates are centralized

| Field | Content |
|---|---|
| Decision | All balance mutations must pass through LedgerService |
| Context | Distributed balance manipulation risks inconsistent financial state |
| Alternatives Rejected | Direct balance modification from multiple services. Rejected because: creates race conditions and consistency failures |
| Consequences | Ledger logic becomes centralized and easier to audit and verify |

---

# ADR-015: Why transactions are append-only

| Field | Content |
|---|---|
| Decision | Transaction records are immutable after creation |
| Context | Financial systems require historical traceability and forensic visibility |
| Alternatives Rejected | Mutable transaction records. Rejected because: historical accuracy becomes unverifiable |
| Consequences | Corrections require compensating transactions rather than record modification |

---

# ADR-016: Why reconciliation jobs exist

| Field | Content |
|---|---|
| Decision | Scheduled reconciliation jobs verify ledger consistency and transaction integrity |
| Context | Operational failures can create state drift or inconsistency |
| Alternatives Rejected | Trusting transactional execution alone. Rejected because: silent corruption may go undetected |
| Consequences | Operational verification becomes part of routine system maintenance |

---

# ADR-017: Why audit logs use hash chaining

| Field | Content |
|---|---|
| Decision | Audit events are cryptographically chained through deterministic hashes |
| Context | Audit systems require tamper-evident verification |
| Alternatives Rejected | Independent audit records without linkage. Rejected because: historical tampering becomes harder to detect |
| Consequences | Audit verification logic becomes more complex, but tamper evidence is significantly stronger |

---

# ADR-018: Why reconciliation is separated from transaction execution

| Field | Content |
|---|---|
| Decision | Reconciliation workflows operate independently from live transaction execution |
| Context | Continuous verification should not slow operational workflows |
| Alternatives Rejected | Inline reconciliation during every transaction. Rejected because: increases latency and operational coupling |
| Consequences | Consistency checks occur periodically rather than during every request |

---

# ADR-019: Why PostgreSQL owns ledger consistency

| Field | Content |
|---|---|
| Decision | Ledger consistency is enforced through PostgreSQL transactional guarantees |
| Context | Financial-style consistency requires atomic state transitions |
| Alternatives Rejected | Eventual consistency approaches. Rejected because: temporary balance inconsistency is unacceptable |
| Consequences | Transaction workflows remain tightly coordinated and ACID-dependent |

---

# ADR-020: Why operational observability is built into the architecture

| Field | Content |
|---|---|
| Decision | Structured logging, correlation IDs, and traceable workflows are mandatory architectural requirements |
| Context | Enterprise systems require diagnosable operational behavior |
| Alternatives Rejected | Minimal logging approaches. Rejected because: operational failures become difficult to investigate |
| Consequences | Additional implementation effort is required, but operational traceability improves significantly |

---

# ADR-021: Why the project prioritizes simulation over production-scale infrastructure

| Field | Content |
|---|---|
| Decision | The platform focuses on enterprise workflow simulation rather than production-scale infrastructure deployment |
| Context | The primary engineering value lies in transaction architecture, auditability, reliability, and operational correctness |
| Alternatives Rejected | Full cloud-native production infrastructure. Rejected because: shifts focus away from core transaction-engineering concepts |
| Consequences | Infrastructure complexity remains controlled while enterprise workflow depth remains high |


## ADR-022: Why GitHub Codespaces is the development environment

| Field | Content |
|---|---|
| Decision | GitHub Codespaces is the mandated development environment |
| Context | The platform requires a reproducible, zero-setup development environment for both the developer and technical reviewers |
| Alternatives Rejected | Local development environments (different Java versions, Docker install issues, operating system inconsistencies). Rejected because: reviewers cannot guarantee identical local setup; "works on my machine" problems are unacceptable for enterprise credibility. Cloud VMs (additional cost, manual setup). Rejected because: Codespaces is included with GitHub. |
| Consequences | Development is browser-based and requires internet. Environment configuration is codified in `.devcontainer/devcontainer.json`. Any reviewer can launch a fresh environment and run `docker-compose up` within minutes without installing anything locally. |

---

# Document Control

| Field | Value |
|---|---|
| Document ID | DOC-4 |
| Version | 2.0 |
| Applies To | ChatGPT |
| Depends On | DOC-1, DOC-2, DOC-3 |

---

**End of Document #4**