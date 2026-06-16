# DOC-8A: API Transport & Application Boundary Specification

## Purpose

This document defines:

* application-layer responsibilities,
* API transport boundaries,
* controller constraints,
* DTO isolation rules,
* request lifecycle behaviour,
* authorization entry boundaries,
* response ownership,
* transport orchestration rules,
* and application-layer architectural constraints.

This document exists to ensure:

* transport concerns remain isolated from domain workflows,
* controllers remain thin,
* APIs remain deterministic,
* external contracts do not corrupt internal models,
* and orchestration ownership remains centralized inside the Domain Layer.

This document should be read alongside:

* DOC-003 (System Architecture),
* DOC-004 (ADR Collection),
* DOC-005 (Domain Model & Lifecycle),
* DOC-006 (Module Boundaries),
* and DOC-007 (Persistence Architecture).

---

# Part 1: Architectural Position

The Application Layer exists between:

* external clients,
* presentation concerns,
* and internal domain workflows.

It acts as:

> a controlled transport boundary.

The Application Layer coordinates:

* request entry,
* transport normalization,
* authorization entry,
* DTO mapping,
* validation,
* response shaping,
* and delegation into domain workflows.

The Application Layer is NOT responsible for:

* business rule ownership,
* ledger mutation ownership,
* workflow sequencing,
* persistence ownership,
* reconciliation ownership,
* or infrastructure implementation.

Primary business orchestration remains owned by:

> Domain Services.

This rule is mandatory across the platform architecture.

---

# Part 2: Architectural Principles

The Application Layer follows the following principles.

| Principle                   | Meaning                                                 |
| --------------------------- | ------------------------------------------------------- |
| Thin controllers            | Controllers handle transport concerns only              |
| Explicit delegation         | Controllers delegate directly to domain workflows       |
| Domain ownership            | Business rules remain inside domain services            |
| DTO isolation               | External contracts remain isolated from domain entities |
| Backend authority           | Backend authorization remains authoritative             |
| Deterministic behaviour     | Valid requests produce predictable outcomes             |
| Request independence        | Workflows must not depend on mutable controller state   |
| Controlled failure handling | Errors return standardized responses                    |
| Anti-corruption boundaries  | External contracts cannot pollute internal models       |
| Transport isolation         | HTTP concerns remain outside business logic             |

---

# Part 3: Layer Responsibilities

The platform follows the four-layer architecture defined in DOC-3.

| Layer                | Responsibility                                |
| -------------------- | --------------------------------------------- |
| Presentation Layer   | Rendering and user interaction                |
| Application Layer    | Request coordination and transport delegation |
| Domain Layer         | Business workflows and invariants             |
| Infrastructure Layer | Persistence, cache, integrations, messaging   |

---

## 3.1 Presentation Layer

The Presentation Layer owns:

* Thymeleaf rendering,
* HTMX interactions,
* form rendering,
* partial updates,
* user interaction flows,
* and response presentation.

The Presentation Layer must NOT own:

* transaction orchestration,
* business workflows,
* authorization policy,
* or persistence behaviour.

---

## 3.2 Application Layer

The Application Layer owns:

* request mapping,
* DTO validation,
* authentication integration,
* authorization entry,
* response mapping,
* correlation propagation,
* and transport delegation.

The Application Layer must NOT own:

* business workflows,
* transaction sequencing,
* domain invariants,
* reconciliation logic,
* ledger consistency,
* or infrastructure orchestration.

---

## 3.3 Domain Layer

The Domain Layer remains:

> the authoritative owner of business orchestration.

The Domain Layer owns:

* transactional workflows,
* lifecycle transitions,
* idempotency enforcement,
* audit generation,
* reconciliation coordination,
* and transactional consistency.

The Domain Layer must remain:

* transport-independent,
* framework-independent,
* and presentation-independent.

---

## 3.4 Infrastructure Layer

The Infrastructure Layer owns:

* PostgreSQL integration,
* Redis integration,
* repository implementations,
* email integration,
* persistence implementation details,
* and infrastructure configuration.

Infrastructure implementations remain hidden behind abstractions defined by the Domain Layer.

---

# Part 4: Request Lifecycle

## Standard Request Flow

```text
Browser Request
      ↓
Controller
      ↓
Authentication Context Resolution
      ↓
Authorization Verification
      ↓
DTO Validation
      ↓
Domain Service Delegation
      ↓
Business Workflow Execution
      ↓
Transactional State Mutation
      ↓
Audit Event Generation
      ↓
Transactional Persistence Finalization
      ↓
Response Mapping
      ↓
HTML or JSON Response
```

---

## Lifecycle Guarantees

| Guarantee                                 | Meaning                                    |
| ----------------------------------------- | ------------------------------------------ |
| Authorization occurs before execution     | Unauthorized requests fail early           |
| Validation occurs at transport boundary   | Invalid requests rejected before workflows |
| Domain workflows own orchestration        | Controllers do not sequence workflows      |
| Audit generation remains transactional    | Audit state remains consistent             |
| Persistence remains implementation detail | Controllers never coordinate repositories  |
| Responses remain deterministic            | Same valid input produces same outcome     |

---

# Part 5: Controller Responsibilities

Controllers are:

> transport adapters only.

Controllers exist to:

* receive requests,
* validate request structure,
* map DTOs,
* invoke authorization entry,
* delegate execution,
* and return responses.

---

## Controllers MAY

Controllers MAY:

* receive HTTP requests,
* validate request shape,
* validate required fields,
* extract request metadata,
* extract authentication context,
* invoke authorization checks,
* map DTOs,
* propagate correlation identifiers,
* delegate to domain services,
* and return mapped responses.

---

## Controllers MUST NOT

Controllers MUST NOT:

* implement business rules,
* coordinate workflows,
* manipulate repositories directly,
* manage transactions,
* coordinate rollback behaviour,
* mutate ledger state,
* generate audit chains,
* implement authorization policy logic,
* coordinate infrastructure concerns,
* or serialize domain entities directly.

Controllers must remain:

* stateless,
* transport-focused,
* and orchestration-light.

---

## Required Delegation Pattern

The following pattern is REQUIRED:

```text
Controller
      ↓
Domain Service
      ↓
Transactional Workflow Execution
```

The following pattern is FORBIDDEN:

```text
Controller
      ↓
Application Service
      ↓
Business Coordination
      ↓
Domain Service
```

This prevents:

* orchestration leakage,
* service-layer ambiguity,
* duplicated workflow sequencing,
* and anemic domain architecture.

---

# Part 6: DTO & Boundary Rules

## DTO Purpose

DTOs exist to isolate:

* transport-layer contracts,
* request structures,
* response structures,
* and presentation-facing models

from:

* domain entities,
* persistence structures,
* and transactional models.

---

## Anti-Corruption Boundary

The Application Layer acts as:

> an anti-corruption boundary.

External contracts must never directly shape:

* domain entities,
* workflow invariants,
* persistence models,
* or ledger structures.

This prevents:

* API-driven domain drift,
* persistence leakage,
* frontend-driven business logic,
* and transport-coupled workflows.

---

## DTO Constraints

| Rule                                 | Meaning                                    |
| ------------------------------------ | ------------------------------------------ |
| DTOs are transport-only              | DTOs contain no business logic             |
| Domain entities remain private       | Internal models are never exposed directly |
| Validation occurs at DTO boundary    | Invalid requests rejected early            |
| Persistence structures remain hidden | Database representation isolated           |
| Responses remain controlled          | Sensitive internal state not leaked        |
| Mapping is explicit                  | DTO-domain conversion is controlled        |
| Serialization boundaries enforced    | Domain entities never serialized directly  |

---

## Explicit Serialization Rule

Domain entities must NEVER:

* be returned directly in transport responses,
* be serialized automatically into APIs,
* or expose persistence-oriented structures externally.

All responses must pass through:

* explicit DTO mapping,
* response shaping,
* and authorization visibility constraints.

---

# Part 7: Authorization Boundaries

Authorization enforcement is mandatory before workflow execution.

The backend remains:

> authoritative for all permission decisions.

Frontend restrictions alone are never trusted.

---

## Authorization Guarantees

| Guarantee                  | Meaning                                        |
| -------------------------- | ---------------------------------------------- |
| Backend authority          | Backend services enforce permissions           |
| Frontend distrust          | Client-side restrictions are not authoritative |
| Explicit permission checks | Restricted workflows require authorization     |
| Least privilege            | Users receive minimum required permissions     |
| Auditor isolation          | Auditor role remains read-only                 |
| Controlled visibility      | Users access only authorized resources         |

---

## Authorization Flow

```text
Request Received
      ↓
Authentication Verification
      ↓
Role Resolution
      ↓
Permission Validation
      ↓
Workflow Delegation Allowed
```

Unauthorized requests must fail:

> before business execution begins.

---

## Forbidden Authorization Patterns

| Forbidden Pattern                     | Reason                           |
| ------------------------------------- | -------------------------------- |
| Frontend-only authorization           | Security bypass risk             |
| Controller-owned authorization policy | Separation-of-concerns violation |
| Role checks after workflow execution  | Unauthorized execution risk      |
| Client-controlled permission flags    | Trust boundary violation         |
| Template-based permission ownership   | Business authorization leakage   |

---

# Part 8: Application Coordination Responsibilities

The Application Layer coordinates:

> transport-level concerns only.

Primary orchestration remains owned by:

> Domain Services.

---

## Application Coordination MAY Include

The Application Layer MAY:

* normalize transport input,
* extract authentication context,
* coordinate DTO mapping,
* coordinate response mapping,
* propagate correlation identifiers,
* invoke authorization entry,
* and delegate execution into domain services.

---

## Application Coordination MUST NOT Include

The Application Layer MUST NOT:

* own domain invariants,
* coordinate business workflows,
* sequence transactional operations,
* manage transaction boundaries,
* manipulate repositories directly,
* mutate ledger state,
* implement reconciliation logic,
* or coordinate infrastructure integrations.

The Application Layer must NEVER become:

* a secondary orchestration layer,
* a persistence coordination layer,
* or a business workflow owner.

---

# Part 9: API Behaviour Rules

The platform enforces deterministic API behaviour.

---

## API Guarantees

| Guarantee                            | Meaning                                          |
| ------------------------------------ | ------------------------------------------------ |
| Duplicate requests remain safe       | Idempotency prevents duplicate outcomes          |
| Concurrent duplicate protection      | Simultaneous requests cannot corrupt state       |
| Invalid transitions fail predictably | Illegal state movement rejected                  |
| Responses remain deterministic       | Same valid request produces same result          |
| Errors remain controlled             | Failures return standardized responses           |
| Audit generation remains automatic   | Important workflows remain traceable             |
| Visibility remains authorized        | Responses filtered through permission boundaries |

---

## Deterministic Behaviour Requirements

The Application Layer must preserve:

* deterministic validation behaviour,
* deterministic authorization enforcement,
* deterministic workflow entry,
* deterministic response mapping,
* and deterministic error handling.

Valid requests with identical:

* workflow state,
* authorization state,
* and idempotency context

must produce:

* predictable workflow outcomes,
* predictable response structures,
* and predictable audit visibility.

---

# Part 10: Response Boundaries

The platform supports two response categories.

| Response Type  | Purpose                       |
| -------------- | ----------------------------- |
| HTML Responses | Thymeleaf + HTMX rendering    |
| JSON Responses | Operational and internal APIs |

---

## Response Constraints

| Constraint                                | Meaning                          |
| ----------------------------------------- | -------------------------------- |
| Internal entities remain hidden           | DTO mapping required             |
| Sensitive state remains protected         | Internal workflow state isolated |
| Responses remain deterministic            | Predictable output structures    |
| Errors remain controlled                  | Failure structures standardized  |
| Domain entities never serialized directly | Transport isolation enforced     |
| Authorization visibility enforced         | Users see only permitted data    |

---

## Response Ownership Rules

The Application Layer owns:

* transport response shape,
* response serialization,
* DTO exposure,
* and visibility filtering.

The Domain Layer owns:

* workflow outcomes,
* business state,
* and transactional behaviour.

---

# Part 11: Alignment With Previous Documents

| Document | Relationship                                          |
| -------- | ----------------------------------------------------- |
| DOC-001  | Defines project scope and enterprise goals            |
| DOC-002  | Defines authorization rules and success criteria      |
| DOC-003  | Defines architecture and layer ownership              |
| DOC-004  | Defines architectural decisions and tradeoffs         |
| DOC-005  | Defines domain invariants and lifecycle guarantees    |
| DOC-006  | Defines module ownership boundaries                   |
| DOC-007  | Defines persistence ownership and storage constraints |

---

# Part 12: Document Control

| Field       | Value                   |
| ----------- | ----------------------- |
| Document ID | DOC-008A                |
| Version     | 1.0                     |
| Applies To  | ChatGPT                 |
| Depends On  | DOC-1 through DOC-7 |

---

**End of DOC-8A**
