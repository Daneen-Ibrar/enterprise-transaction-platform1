# Document #9A: Identity, Authentication & Authorization Architecture

## Purpose

This document defines:

- identity and access architecture,
- authentication responsibilities,
- session management strategy,
- role-based access control (RBAC),
- authorization enforcement rules,
- trust boundaries,
- and security ownership constraints.

This document exists to ensure:

- backend authorization remains authoritative,
- user identity remains verifiable,
- privileged operations remain restricted,
- transport concerns remain isolated from domain workflows,
- and authorization enforcement remains non-bypassable.

This document should be read alongside:

- DOC-002 (AI Boundaries & Success Criteria),
- DOC-003 (System Architecture),
- DOC-004 (ADR Collection),
- DOC-006 (Module Ownership),
- DOC-008A (API Transport Boundaries),
- and DOC-008B (Transactional Behaviour & Observability).

---

# Part 1: Security Architecture Principles

The platform follows mandatory identity and authorization principles.

| Principle | Meaning |
|---|---|
| Backend authority | Authorization decisions are made exclusively in backend services |
| Least privilege | Users receive only permissions required for their role |
| Secure by default | Security failures default to rejection |
| Defence in depth | Authentication, authorization, validation, and audit operate together |
| Transport isolation | HTTP/session concerns remain outside domain workflows |
| Observable authorization | Authorization decisions remain traceable |
| Non-bypassable enforcement | Protected workflows cannot skip authorization |
| Credential isolation | Credentials and secrets remain protected |

---

# Part 1.1: Trust Boundaries

The platform explicitly defines trust boundaries between architectural layers and external actors.

All boundary crossings require:

- validation,
- authorization,
- controlled serialization,
- and observability.

---

## Trust Boundary Matrix

| Boundary | Trust Level | Protection Requirements |
|---|---|---|
| Browser Client → Application Layer | Untrusted | DTO validation, authentication, authorization |
| HTMX Requests → Controllers | Untrusted | CSRF protection, validation, authorization |
| Application Layer → Domain Layer | Trusted internal boundary | DTO mapping enforced |
| Domain Layer → Infrastructure Layer | Trusted internal boundary | Repository abstraction enforced |
| Runtime Environment → Application | Trusted deployment boundary | Secret injection only |

---

## Trust Boundary Rules

The platform assumes:

- all client input is untrusted,
- frontend state is non-authoritative,
- and business invariants must only be enforced server-side.

No external actor may directly:

- mutate transactional state,
- bypass authorization,
- influence domain invariants,
- or access privileged workflows without backend validation.

---

# Part 2: Authentication & Session Management

## Authentication Responsibilities

| Aspect | Ownership |
|---|---|
| Credential validation | Identity & Access Module |
| Session establishment | Identity & Access Module |
| Session validation | Identity & Access Module |
| Password hashing | Infrastructure Layer |
| Authentication interception | Application Layer |

---

## Session Architecture

The platform uses:

> server-managed authenticated session state.

This aligns with:

- Spring Security,
- Thymeleaf,
- HTMX,
- and server-driven rendering architecture.

---

## Session Constraints

| Constraint | Meaning |
|---|---|
| Session state isolated from domain workflows | Domain layer remains transport-independent |
| Authentication validated per request | No implicit trust |
| Session expiration enforced | Idle sessions terminated |
| Session cookies HTTP-only | JavaScript access restricted |
| Secure cookie flags enabled in production | Transport protection |
| CSRF protection enabled | Stateful session protection |

---

## Authentication Guarantees

| Guarantee | Meaning |
|---|---|
| Credentials never stored in plaintext | BCrypt hashing required |
| Failed authentication attempts logged | Security visibility preserved |
| Session state isolated from domain logic | Prevents transport coupling |
| Authentication failures return controlled responses | Prevents information leakage |
| Authentication evaluated before authorization | Identity verified first |

---

## Explicit Non-Goals

The platform does NOT currently use:

- stateless JWT authentication,
- SPA-managed authentication state,
- frontend token orchestration,
- or external OAuth identity providers.

---

## Forbidden Authentication Patterns

| Forbidden Pattern | Reason |
|---|---|
| Hardcoded credentials | Security exposure |
| Plaintext password storage | Critical security violation |
| Credential logging | Sensitive data leakage |
| Session state inside domain workflows | Violates transport isolation |
| Authentication logic inside templates | Bypass risk |

---

# Part 3: Role-Based Access Control (RBAC) – Policy‑Driven

Authorization is **fully policy‑driven**. No role or permission names are hardcoded in application logic.

The platform defines four standard roles (Customer, Merchant, Admin, Auditor) as **seed data**, not as code constants. These roles, their permissions, and the role‑permission mappings are stored in database tables defined in **DOC-9C: Policy‑Driven Authorization Schema**.

The logical permission matrix (for reference) is:

| Action | Customer | Merchant | Admin | Auditor |
|--------|----------|----------|-------|---------|
| View own invoices | ✅ | ✅ | ✅ | ❌ |
| View all invoices | ❌ | ❌ | ✅ | ✅ |
| Create invoice | ❌ | ✅ | ✅ | ❌ |
| Pay invoice | ✅ | ❌ | ❌ | ❌ |
| Approve high‑value invoice | ❌ | ❌ | ✅ | ❌ |
| Issue refund | ❌ | ❌ | ✅ | ❌ |
| View audit log | ❌ | ❌ | ❌ | ✅ |
| Verify audit integrity | ❌ | ❌ | ❌ | ✅ |
| Run reconciliation | ❌ | ❌ | ✅ | ❌ |
| View reconciliation reports | ❌ | ❌ | ✅ | ✅ |

> **Important:** This matrix is **documentation only**. The actual enforcement must be based on the `permission` and `role_permission` tables (DOC‑9C). No inline `if (role == "ADMIN")` checks are permitted.

## Authorization Enforcement Rules

| Rule | Meaning |
|---|---|
| Backend authoritative | Frontend restrictions are informational only |
| Enforce before execution | Permission checks occur before business workflows |
| Auditor remains read-only | Auditor role cannot mutate state |
| Unauthorized requests return 403 | Consistent failure response |
| Role ownership centralized | Permissions managed centrally |

---

# Part 3.1: Security Ownership Boundaries

Security responsibilities are explicitly partitioned across architectural layers and modules.

---

## Security Ownership Matrix

| Security Concern | Primary Owner |
|---|---|
| Authentication | Identity & Access Module |
| Session Validation | Application Layer |
| Authorization Enforcement | Domain workflow entry |
| Role Resolution | Identity & Access Module |
| Credential Hashing | Infrastructure Layer |
| Secret Injection | Runtime Environment |
| Transport Security | Application Infrastructure |
| Persistence Access Control | Infrastructure Layer |

---

## Ownership Constraints

Security ownership must remain centralized.

For example:

- controllers must not own authorization policy,
- templates must not define permissions,
- infrastructure must not define business authorization,
- and frontend visibility rules must not replace backend enforcement.

This prevents:

- fragmented security logic,
- inconsistent enforcement,
- and authorization bypass paths.

---

## Forbidden RBAC Patterns

| Forbidden Pattern | Reason |
|---|---|
| Frontend-only role hiding | Bypassable security risk |
| Hardcoded role checks in controllers | Violates separation of concerns |
| Template-owned authorization | Security logic leakage |
| Auditor write access | Compliance violation |
| Business permissions inside infrastructure layer | Violates ownership boundaries |

---

# Part 4: Authorization Enforcement Flow

## Standard Authorization Flow

```text
Request Received
      ↓
Authentication Verification
      ↓
Role Resolution
      ↓
Permission Validation
      ↓
ALLOW or DENY
      ↓
If DENY → 403 Forbidden + Audit Event
      ↓
If ALLOW → Continue to Domain Workflow
```

---

## Authorization Requirements

| Requirement | Meaning |
|---|---|
| Per-endpoint enforcement | Every protected endpoint validates authorization |
| No bypass paths | All workflow entry points protected |
| Consistent denial response | Controlled 403 responses |
| Audit visibility | Denials remain observable |
| Authorization before business execution | Prevents illegal workflow entry |

---

## Authorization Constraints

Authorization enforcement must remain:

- backend authoritative,
- deterministic,
- centralized,
- and observable.

Authorization must NEVER depend on:

- frontend state,
- UI visibility,
- hidden buttons,
- or client-side workflow restrictions.

---

# Part 5: Identity & Access Constraints

## Identity Architecture Constraints

| Constraint | Meaning |
|---|---|
| Authentication isolated from domain workflows | Domain layer remains transport-independent |
| Authorization centralized | Consistent permission enforcement |
| User identity immutable during request lifecycle | Prevents mid-request mutation |
| Session context transport-scoped | Prevents leakage into domain models |
| Identity validation required before state mutation | Unauthorized mutation prevented |

---

## Security Event Requirements

The following events must remain observable:

- login success,
- login failure,
- logout,
- authorization denial,
- role assignment,
- session expiration,
- and privileged operation access.

---

## Identity Failure Handling

| Scenario | Response |
|---|---|
| Invalid credentials | 401 Unauthorized |
| Missing session | 401 Unauthorized |
| Insufficient role | 403 Forbidden |
| Invalid permission mapping | Deny by default |
| Session expiration | Forced re-authentication |

---

# Part 6: Security Alignment with ADRs

| ADR | Relevance |
|---|---|
| ADR-007 | Backend authorization authoritative |
| ADR-008 | Server-driven frontend architecture |
| ADR-014 | Audit visibility for workflow operations |
| ADR-019 | Observability and traceability |

---

# Part 7: Alignment with Previous Documents

| Document | Relationship |
|---|---|
| DOC-2 | Authorization boundaries and success criteria |
| DOC-3 | Layer isolation and ownership |
| DOC-4 | Security-related architectural decisions |
| DOC-6 | Identity & Access Module ownership |
| DOC-8A | Controller and transport boundary enforcement |
| DOC-8B | Observability and transactional behaviour |
| DOC-9C | Policy‑driven authorization schema and permission storage |

---

# Part 8: Glossary

| Term | Meaning |
|---|---|
| RBAC | Role-Based Access Control |
| Least Privilege | Users receive minimum required permissions |
| Backend Authority | Authorization enforced server-side |
| Trust Boundary | Boundary separating trusted and untrusted actors |
| Session Isolation | Session state separated from domain workflows |
| Transport Isolation | HTTP concerns isolated from business logic |

---

# Part 9: Document Control

| Field | Value |
|---|---|
| Document ID | DOC-9A |
| Version | 1.0 |
| Applies To | ChatGPT |
| Depends On | DOC-1 through DOC-9C |

---

**End of Document #9A**