# Document #12A: Runtime Topology & Deployment Architecture

## Purpose

This document defines:

- runtime topology and container boundaries
- environment separation and deployment targets
- containerization rules and orchestration constraints
- infrastructure ownership and authority boundaries
- startup ordering and dependency management
- network boundaries and service isolation
- configuration management and profile strategy
- deployment non-goals

---


# ⚠️ CONFIGURATION IS POLICY-DRIVEN

All runtime configuration MUST be externalised.

The system MUST NOT assume:

- fixed ports
- fixed hostnames
- fixed credentials
- fixed topology

---

# Part 1: Runtime Topology

## Logical Runtime Components

The system consists of three core runtime components:

| Component | Responsibility | Authority |
|-----------|---------------|-----------|
| Application Runtime | Business logic + orchestration | Business authority |
| PostgreSQL | Persistent system of record | Source of truth |
| Redis | Ephemeral coordination + idempotency support | Non-authoritative |

## Dependency Rules

| Dependency | Requirement |
|------------|-------------|
| Application → PostgreSQL | Required |
| Application → Redis | Optional (degraded mode) |
| PostgreSQL → other services | None |
| Redis → other services | None |

## Responsibility Boundaries

| Layer | Owns | Does NOT Own |
|-------|------|--------------|
| Application | Business logic, orchestration | Persistence implementation |
| PostgreSQL | Durable state, transactions | Business rules |
| Redis | Ephemeral coordination | System of record |

---

# Part 2: Environment Separation

| Environment | Purpose | Data Type |
|-------------|---------|-----------|
| Local | Development | Ephemeral |
| CI | Validation | Disposable |
| Production | Live system | Persistent |

**Rule:** No production data in non-production environments.

---

# Part 3: Containerization Rules

Services are configured via environment variables only:

DATABASE_URL=${DATABASE_URL}
CACHE_URL=${CACHE_URL}

---

# Part 4: Startup Ordering

Startup is governed by readiness, not fixed order.

Database → ready signal  
Cache → optional readiness  
Application → depends on all services

---

# Part 5: Network Boundaries

- Application: external access allowed  
- Database: internal only  
- Cache: internal only  

**Rule:** No hardcoded IPs, ports, or hostnames.

---

# Part 6: Configuration

Priority:

1. Environment variables  
2. Deployment config  
3. Defaults  

**Rule:** Secrets must NEVER be embedded.

---

# Part 7: Deployment Constraints

- No Kubernetes  
- No service mesh  
- No multi-region  
- Single deployment unit only  

---

# Part 8: System Model

Application is the single runtime orchestrator.

PostgreSQL = source of truth  
Redis = ephemeral coordination  

---

# Part 9: Control

| Document | Purpose |
|----------|--------|
| DOC-3 | Architecture |
| DOC-7 | Persistence |
| DOC-10A | Runtime policy |
| DOC-10B | Recovery |

---

# Part 10: Document Control

| Field | Value |
|-------|-------|
| Document ID | DOC-12A |
| Version | 3.2 |
| Depends On | DOC-3, DOC-7, DOC-10A |

---