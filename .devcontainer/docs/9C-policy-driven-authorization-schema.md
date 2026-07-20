# Document #9C: Policy-Driven Authorization Schema

## Purpose

This document defines the database schema and operational rules for policy-driven authorization within the Enterprise Transaction Orchestration & Audit Platform.

The platform intentionally avoids hardcoded authorization logic.

All:

* roles,
* permissions,
* permission mappings,
* and authorization relationships

must be stored and evaluated dynamically through PostgreSQL-backed policy data.

Authorization decisions are evaluated at runtime through database-driven policy resolution.

This document defines:

* authorization schema structure,
* permission mapping rules,
* runtime evaluation guarantees,
* operational constraints,
* and security invariants.

---

# Core Architectural Principles

| Principle                        | Meaning                                                                      |
| -------------------------------- | ---------------------------------------------------------------------------- |
| No hardcoded authorization       | Authorization decisions must never rely on inline role checks                |
| Policy-driven evaluation         | Permissions are resolved dynamically at runtime                              |
| Centralized permission ownership | Authorization rules originate from a single authoritative source             |
| Least privilege                  | Roles receive only required permissions                                      |
| Auditability                     | Authorization changes remain historically traceable                          |
| Deterministic evaluation         | The same role and permission state produces consistent authorization results |

---

# Part 1: Authorization Schema

## 1.1 Role Table

### `role`

| Column      | Type                          | Description                     |
| ----------- | ----------------------------- | ------------------------------- |
| id          | BIGINT PRIMARY KEY            | Surrogate key                   |
| name        | VARCHAR(50) UNIQUE NOT NULL   | Unique role identifier          |
| description | VARCHAR(255) NULL             | Human-readable role description |
| active      | BOOLEAN NOT NULL DEFAULT TRUE | Enables soft deactivation       |
| created_at  | TIMESTAMP NOT NULL            | Creation timestamp              |

---

### Example Roles

* CUSTOMER
* MERCHANT
* ADMIN
* AUDITOR

Role names are identifiers only and must not imply hardcoded authorization behaviour inside application logic.

---

## 1.2 Permission Table

### `permission`

| Column      | Type                  | Description                |
| ----------- | --------------------- | -------------------------- |
| id          | BIGINT PRIMARY KEY    | Surrogate key              |
| resource    | VARCHAR(100) NOT NULL | Protected resource         |
| action      | VARCHAR(50) NOT NULL  | Allowed action             |
| description | VARCHAR(255) NULL     | Human-readable description |
| created_at  | TIMESTAMP NOT NULL    | Creation timestamp         |

---

### Permission Naming Rules

Permissions follow a resource-action structure.

Examples:

| Resource       | Action   |
| -------------- | -------- |
| invoice        | create   |
| invoice        | approve  |
| invoice        | view_own |
| payment        | refund   |
| audit          | verify   |
| reconciliation | run      |

---

### Constraints

Unique constraint:

```sql id="gk7z9x"
UNIQUE(resource, action)
```

This prevents duplicate permission definitions.

---

## 1.3 Role-Permission Mapping Table

### `role_permission`

| Column                               | Type                             | Description                   |
| ------------------------------------ | -------------------------------- | ----------------------------- |
| role_id                              | BIGINT REFERENCES role(id)       | Associated role               |
| permission_id                        | BIGINT REFERENCES permission(id) | Associated permission         |
| granted_at                           | TIMESTAMP NOT NULL               | Grant timestamp               |
| granted_by                           | BIGINT NULL                      | Optional administrative actor |
| PRIMARY KEY (role_id, permission_id) |                                  | Composite mapping key         |

---

## 1.4 User-Role Assignment Table

### `user_role`

| Column                         | Type                       | Description           |
| ------------------------------ | -------------------------- | --------------------- |
| user_id                        | BIGINT NOT NULL            | Associated user       |
| role_id                        | BIGINT REFERENCES role(id) | Assigned role         |
| assigned_at                    | TIMESTAMP NOT NULL         | Assignment timestamp  |
| assigned_by                    | BIGINT NULL                | Administrative actor  |
| PRIMARY KEY (user_id, role_id) |                            | Composite mapping key |

---

# Part 2: Runtime Authorization Evaluation

## 2.1 Authorization Flow

Authorization is evaluated dynamically during request processing.

Conceptual flow:

```text id="kmn2sf"
Authenticated user
→ Resolve assigned roles
→ Resolve role-permission mappings
→ Evaluate requested resource/action
→ Grant or deny access
```

---

## 2.2 Multi-Role Resolution

Users may possess multiple roles simultaneously.

Authorization evaluation follows additive permission resolution:

* permissions are unioned across assigned roles,
* explicit deny rules are outside current MVP scope,
* and duplicate permissions collapse into a single effective permission set.

---

## 2.3 Runtime Guarantees

| Guarantee                         | Meaning                                                  |
| --------------------------------- | -------------------------------------------------------- |
| No hardcoded fallback             | Authorization must never bypass policy tables            |
| Backend-authoritative enforcement | Frontend visibility does not determine authorization     |
| Deterministic evaluation          | Same inputs produce same authorization result            |
| Policy consistency                | Permission mappings remain centrally managed             |
| Revocation consistency            | Disabled roles immediately lose authorization capability |

---

# Part 3: Operational Constraints

The platform intentionally forbids:

| Forbidden Behaviour                                 | Reason                              |
| --------------------------------------------------- | ----------------------------------- |
| Inline role checks (`if ADMIN`)                     | Violates policy-driven architecture |
| Hardcoded permission mappings                       | Reduces maintainability             |
| Frontend-only authorization                         | Security risk                       |
| Authorization caching without invalidation strategy | Risks stale permissions             |
| Multiple sources of truth for permissions           | Causes inconsistent enforcement     |

> **Important:** This matrix is **documentation only**. The actual enforcement must be based on the `permission` and `role_permission` tables (DOC‑9C). No inline `if (role == "ADMIN")` checks are permitted.

**Workflow‑specific policies** (e.g., approval thresholds, refund eligibility) are defined in separate policy tables owned by their respective modules. See **DOC‑9D: Policy‑Driven Workflow Rules (Approval & Refund)** for the schema and evaluation rules.

---

# Part 4: Authorization Caching Rules

Authorization caching is optional and must follow these rules:

| Rule                              | Requirement                                      |
| --------------------------------- | ------------------------------------------------ |
| PostgreSQL remains authoritative  | Cache is performance optimization only           |
| Cache invalidation required       | Permission changes must invalidate stale entries |
| Cache TTL externally configurable | No hardcoded TTL values                          |
| Failed cache lookup fallback      | Re-query database safely                         |

Redis may be used for short-lived authorization caching, but authorization correctness must never depend solely on cache state.

---

# Part 5: Auditability Requirements

Authorization changes are security-sensitive operations.

The following events must be auditable:

* role creation,
* permission creation,
* role-permission assignment,
* user-role assignment,
* role deactivation,
* and authorization policy modification.

Audit records must remain:

* append-only,
* tamper-evident,
* and historically traceable.

---

# Part 6: Example Seed Data (Illustrative Only)

```sql id="jqv0ci"
INSERT INTO role (name)
VALUES
('CUSTOMER'),
('MERCHANT'),
('ADMIN'),
('AUDITOR');

INSERT INTO permission (resource, action)
VALUES
('invoice', 'view_own'),
('invoice', 'view_all'),
('invoice', 'create'),
('invoice', 'pay'),
('invoice', 'approve'),
('payment', 'refund'),
('audit', 'view'),
('audit', 'verify'),
('reconciliation', 'run'),
('reconciliation', 'view_report');
```

Role-permission mappings are intentionally omitted because authorization policy may evolve between environments.

---

# Part 7: Alignment with Existing Documents

| Document | Relationship                                                |
| -------- | ----------------------------------------------------------- |
| DOC-3  | Defines application and domain layer responsibilities       |
| DOC-6  | Defines Identity & Access Module ownership                  |
| DOC-9A | Defines RBAC architecture and authentication model          |
| DOC-9B | Defines authorization threat model and security constraints |
| DOC-10A | Aligns with policy-driven runtime evaluation principles     |

---

# Part 8: Document Control

| Field       | Value                                          |
| ----------- | ---------------------------------------------- |
| Document ID | DOC-9C                                       |
| Version     | 2.0                                            |
| Type        | Security Architecture                          |
| Depends On  | DOC-3, DOC-6, DOC-9A, DOC-9B, DOC-10A |

---

**End of DOC-9C**
