# Document #9B: Threat Model, Audit Integrity & Security Operations

## Purpose

This document defines:

- audit integrity guarantees,
- threat modelling and attack surfaces,
- abuse prevention mechanisms,
- secret management constraints,
- security observability architecture,
- operational security boundaries,
- and security failure handling behaviour.

This document exists to ensure:

- audit history remains tamper-evident,
- operational threats remain observable,
- abuse scenarios remain constrained,
- secrets remain protected,
- and security failures remain diagnosable.

This document should be read alongside:

- DOC-003 (System Architecture),
- DOC-004 (ADR Collection),
- DOC-005 (Domain Lifecycle & Invariants),
- DOC-007 (Persistence & Audit Ownership),
- DOC-008B (Transactional Behaviour & Observability),
- and DOC-009A (Identity & Authorization Architecture).

---

## Audit Integrity Guarantees

- Append-only
- Tamper-evident (hash chaining)
- Immutable history
- Verification capable
- Observable generation

**Hash Chain Algorithm**:
All audit hash chains SHALL use **SHA‑256** as defined in FIPS 180‑4.
For each event:
  previous_hash = hash of the concatenation of (previous_event_hash + event_payload)
The implementation must treat `null` previous hash as a sentinel (first event).

## Audit Requirements

| Requirement | Meaning |
|---|---|
| Append-only | Audit records cannot be modified or deleted |
| Tamper-evident | Hash chaining detects modification attempts |
| Immutable history | Historical records remain visible |
| Verification capable | Audit integrity independently verifiable |
| Observable generation | Audit failures remain detectable |

---

## Actions That MUST Be Logged

| Category | Actions |
|---|---|
| Authentication | Login, logout, failed login attempts |
| Authorization | Permission denials, role assignments |
| Invoice workflow | Creation, approval, rejection |
| Payment workflow | Initiation, completion, failure |
| Refund workflow | Issuance, approval |
| Reconciliation operations | Execution and report generation |
| Audit operations | Integrity verification requests |
| Administrative actions | Configuration and privileged operations |
| Data mutations | Any state-changing workflow |

---

## Audit Integrity Constraints

| Constraint | Meaning |
|---|---|
| No silent failures | Audit generation failures observable |
| No bypass paths | All state mutation workflows audited |
| No deletion capability | Audit records immutable |
| Timestamp integrity | Accurate workflow chronology |
| Hash-chain continuity | Historical sequence protection |

---

## Audit Failure Behaviour

If audit generation fails:

- transactional workflows must fail safely,
- failures must remain observable,
- and operational alerts must be generated.

The system must NEVER:

- silently continue state mutation without audit generation,
- or permit partial audit persistence.

---

# Part 2: Threat Model & Attack Surfaces

The platform models both:

- external attacker threats,
- authenticated malicious actors,
- insider abuse,
- infrastructure compromise,
- and operational failures.

---

## Threat Categories

| Threat Type | Examples | Mitigation |
|---|---|---|
| External attacker | Credential brute force, injection attacks | Validation, BCrypt, rate limiting |
| Authenticated malicious actor | Unauthorized refunds, privilege escalation | RBAC enforcement, audit visibility |
| Insider/admin abuse | Unauthorized role assignment, audit tampering | Least privilege, audit observability |
| Infrastructure compromise | Secret leakage, DB credential exposure | Environment isolation |
| Operational failure | Logging outage, cache failure | Observability and fallback handling |
| Replay attacks | Duplicate payment execution | Idempotency enforcement |

---

## Attack Surface Inventory

| Surface | Exposure | Protection |
|---|---|---|
| Public API endpoints | HTTP/HTTPS | Authentication and authorization |
| Authentication endpoints | Login, registration | Rate limiting |
| Audit endpoints | Read-only access | Restricted roles |
| Admin endpoints | Operational workflows | Elevated permissions |
| Database (PostgreSQL) | Internal network only | Credential isolation |
| Redis cache | Internal only | No direct external access |

---

## Privileged Operation Protection

| Operation | Protection |
|---|---|
| Reconciliation execution | Admin role + audit logging |
| Audit verification | Auditor role + read-only |
| Refund issuance | Admin role + reason required |
| Role assignment | Admin role + audit visibility |

---

## Threat Modelling Constraints

Threat mitigation must remain:

- backend authoritative,
- observable,
- deterministic,
- and layered.

Security mechanisms must NEVER rely exclusively on:

- frontend restrictions,
- hidden UI controls,
- or client-managed workflow rules.

---

# Part 3: Data Protection Requirements

## Sensitive Data Classification

| Classification | Examples | Protection |
|---|---|---|
| Public | Invoice amounts, workflow status | No protection required |
| Internal | User emails, role assignments | Authentication required |
| Sensitive | Password hashes, audit hashes | BCrypt, hash chaining |
| Secret | Database credentials, session secrets | Environment isolation |

---

## Data Protection Rules

| Rule | Meaning |
|---|---|
| No secrets in source control | Prevent credential exposure |
| No credential logging | Passwords and tokens excluded from logs |
| No plaintext passwords | BCrypt hashing mandatory |
| No exposed internal entities | DTO mapping required |
| No stack traces in production | Controlled error responses |

---

## Transport Security Constraints

| Aspect | Requirement |
|---|---|
| Local development | HTTP acceptable (non-public only) |
| Exposed environments | HTTPS required |
| Secret transmission | Encrypted transport only |
| API credentials | Environment variables only |

---

# Part 3.1: Secret Management Constraints

Secrets are treated as high-sensitivity operational assets.

---

## Secret Management Rules

| Rule | Meaning |
|---|---|
| Secrets never committed to source control | Prevent accidental exposure |
| Runtime injection only | Secrets supplied during deployment |
| Environment isolation required | Dev/test/prod separation |
| Secret rotation supported | Credentials replaceable without redesign |
| No secret logging | Sensitive values excluded from logs |
| No frontend exposure | Secrets never exposed to clients |

---

## Secret Categories

| Secret Type | Example |
|---|---|
| Database credentials | PostgreSQL password |
| Session secrets | Spring session secret |
| API credentials | External integration keys |
| Audit signing secrets | Hash-chain integrity keys |

---

## Secret Management Non-Goals

The platform does NOT currently implement:

- dedicated secrets vault infrastructure,
- hardware security modules (HSMs),
- or automated credential rotation.

These are considered:

> future operational scaling concerns.

---

# Part 4: Abuse Prevention

The platform must remain resistant to:

- workflow abuse,
- request flooding,
- replay attacks,
- and unauthorized operational access.

---

## Rate Limiting (Planned for v2)

| Endpoint Category | Limit | Status |
|---|---|---|
| Authentication | 5 attempts/minute | Planned |
| Payment submission | 10 requests/minute | Planned |
| Invoice creation | 20 requests/minute | Planned |
| Audit queries | 30 requests/minute | Planned |
| Admin operations | 50 requests/minute | Planned |

---

## Input Validation Requirements

| Rule | Meaning |
|---|---|
| DTO validation mandatory | Invalid requests rejected early |
| Size limits enforced | Resource exhaustion prevention |
| Character restrictions enforced | Injection mitigation |
| Business validation domain-owned | Workflow invariants centralized |

---

## Replay Protection

| Protection | Purpose |
|---|---|
| Idempotency keys | Prevent duplicate workflow execution |
| Duplicate request detection | Prevent payment replay |
| Transactional consistency checks | Prevent concurrent corruption |

---

## Abuse Detection

| Detection | Action |
|---|---|
| Repeated login failures | Logged and monitored |
| Excessive permission denials | Security investigation |
| Unusual workflow patterns | Operational alert |
| Repeated replay attempts | Potential abuse investigation |

---

# Part 4.1: Security Observability Separation

The platform distinguishes between multiple observability categories.

---

## Observability Categories

| Category | Purpose |
|---|---|
| Audit Logging | Compliance and tamper evidence |
| Security Event Logging | Threat monitoring and abuse detection |
| Operational Logging | Diagnostics and troubleshooting |
| Metrics & Monitoring | Health and performance visibility |

---

## Correlation Requirements

All security-relevant events must support:

- correlation identifiers,
- workflow traceability,
- operational diagnostics,
- and audit linkage.

---

## Observability Constraints

Security observability must remain:

- centralized,
- searchable,
- correlation-aware,
- and operationally accessible.

Audit logging must NEVER be treated as a replacement for:

- operational diagnostics,
- monitoring,
- or alerting infrastructure.

---

# Part 5: Security Failure Handling

Security failures must remain:

- deterministic,
- observable,
- controlled,
- and fail-secure.

---

## Failure Response Rules

| Rule | Meaning |
|---|---|
| No internal detail leakage | Stack traces hidden |
| Controlled response structure | Consistent failures |
| Audit visibility preserved | Security failures logged |
| Fail secure by default | Rejection preferred over acceptance |

---

## Security Failure Examples

| Scenario | Response | Audit Behaviour |
|---|---|---|
| Invalid credentials | 401 Unauthorized | Failed login logged |
| Missing session | 401 Unauthorized | Minimal logging |
| Insufficient role | 403 Forbidden | Permission denial logged |
| Audit tampering detected | 500 Internal + alert | Tampering event recorded |
| Secret injection failure | Startup failure | Operational alert |

---

## Failure Isolation Constraints

Security failures must NEVER:

- expose secrets,
- leak infrastructure details,
- expose internal stack traces,
- or partially commit transactional state.

---

# Part 6: Explicit Security Non-Goals

The platform intentionally does NOT currently guarantee:

- PCI-DSS certification,
- zero-trust infrastructure,
- intrusion detection systems,
- behavioural anomaly detection,
- enterprise SIEM integration,
- hardware-backed secrets management,
- or multi-region disaster recovery.

These concerns are considered:

> future operational scaling concerns rather than MVP architectural requirements.

---

# Part 7: Security Alignment with ADRs

| ADR | Relevance |
|---|---|
| ADR-002 | PostgreSQL authoritative persistence |
| ADR-004 | Append-only audit architecture |
| ADR-014 | Audit workflow guarantees |
| ADR-016 | Hash-chained audit integrity |
| ADR-018 | Transaction consistency ownership |
| ADR-019 | Observability and operational traceability |

---

# Part 8: Alignment with Previous Documents

| Document | Relationship |
|---|---|
| DOC-3 | Architectural ownership boundaries |
| DOC-4 | Security and audit ADRs |
| DOC-5 | Audit and transactional invariants |
| DOC-7 | Persistence and audit ownership |
| DOC-8B | Transaction observability and failure semantics |
| DOC-9A | Identity and authorization enforcement |

---

# Part 9: Glossary

| Term | Meaning |
|---|---|
| Append-only | Records cannot be modified or deleted |
| Tamper-evident | Modification attempts detectable |
| Replay Attack | Duplicate request resubmission |
| Defence in Depth | Multiple independent security layers |
| Secret Rotation | Credential replacement without redesign |
| Fail Secure | Failure defaults to rejection |

---

# Part 10: Document Control

| Field | Value |
|---|---|
| Document ID | DOC-009B |
| Version | 1.0 |
| Applies To | ChatGPT |
| Depends On | DOC-1 through DOC-9A |

---

**End of Document #9B**