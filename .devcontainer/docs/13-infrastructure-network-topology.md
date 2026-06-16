# Document #13: Infrastructure & Network Topology

## Purpose

This document defines:

- network boundary rules
- service-to-service communication patterns
- external access policies
- infrastructure isolation boundaries
- environment-specific network assumptions
- infrastructure non-goals

This document exists to ensure:

- network boundaries remain explicit
- service communication is policy-driven
- no hardcoded network assumptions exist
- infrastructure complexity remains bounded

This document should be read alongside:

- DOC-3 (System Architecture)
- DOC-7 (Persistence & Data Ownership)
- DOC-12A (Runtime Topology)
- DOC-12B (Startup Sequencing)

---

# ⚠️ NETWORK CONFIGURATION IS POLICY-DRIVEN

The system MUST NOT assume:

- fixed IP addresses
- fixed hostnames
- fixed ports
- specific network topology
- specific cloud provider

All network configuration MUST be externalised via environment variables or deployment configuration.

---

# Part 1: Service Communication Matrix

## Internal Services

| Source | Destination | Communication Pattern | Required |
|--------|-------------|----------------------|----------|
| Application | PostgreSQL | Direct (JDBC) | Yes |
| Application | Redis | Direct (client) | No (degraded) |
| PostgreSQL | Any other service | None | N/A |
| Redis | Any other service | None | N/A |

---

## External Access

| Service | External Access | Purpose |
|---------|----------------|----------|
| Application | Yes (configurable port) | API / Clients |
| PostgreSQL | No | Internal system |
| Redis | No | Internal system |

---

# Part 2: Network Boundary Rules

## Isolation Rules

| Rule | Meaning |
|------|--------|
| Database isolated | No external PostgreSQL access |
| Cache isolated | No external Redis access |
| Application exposed | Only public entry point |
| Internal discovery | Environment-driven only |

---

## Hard Constraint

The system MUST NOT assume:

- IP ranges
- DNS names
- cloud VPC layout
- load balancer topology

---

# Part 3: Environment-Specific Network Patterns

## Local Development

| Service | Access |
|--------|--------|
| Application | http://localhost:${PORT} |
| PostgreSQL | internal container network |
| Redis | internal container network |

---

## Production (Conceptual)

| Service | Access Pattern |
|--------|----------------|
| Application | Load balancer / reverse proxy |
| PostgreSQL | Private subnet only |
| Redis | Private subnet only |

**Rule:** Application code MUST NOT differentiate environments.

---

# Part 4: Communication Security

## Security Rules

| Area | MVP | Future |
|------|-----|--------|
| Internal encryption | Optional | Recommended |
| External traffic | HTTP allowed | HTTPS required |
| Database TLS | Optional | Recommended |

---

## Trust Boundaries

- Application is the only external trust boundary
- Database trusts only Application
- Cache trusts only Application

---

# Part 5: Infrastructure Non-Goals

The following are explicitly excluded:

- Kubernetes networking policies
- service mesh (Istio / Linkerd)
- multi-region networking
- CDN configuration
- WAF / DDoS tooling
- VPC design
- infrastructure-as-code networking

---

# Part 6: Failure & Degradation

## Network Failure Modes

| Failure | Impact | Recovery |
|--------|--------|----------|
| Database unreachable | System unavailable | Restart / restore |
| Redis unreachable | Degraded mode | Continue safely |
| Application unreachable | Full outage | Restart |

---

## Degraded Mode Behaviour

When Redis is unavailable:

- Idempotency cache disabled
- Duplicate detection degraded but safe
- Application continues normal operation

---

# Part 7: Alignment

| Document | Relationship |
|----------|-------------|
| DOC-3 | Architecture boundaries |
| DOC-7 | Persistence isolation |
| DOC-12A | Runtime topology |
| DOC-12B | Startup dependencies |

---

# Part 8: Glossary

| Term | Meaning |
|------|--------|
| Internal Service | Not exposed externally |
| External Access | Client-facing entry point |
| Network Boundary | Trust separation layer |
| Degraded Mode | Reduced functionality, safe operation |

---

# Part 9: Document Control

| Field | Value |
|------|------|
| Document ID | DOC-13 |
| Version | 1.0 |
| Depends On | DOC-3, DOC-7, DOC-12A, DOC-12B |

---

**End of Document #13**