# Document #14: Scalability & Evolution Notes

## Purpose

This document defines:

- future scalability considerations
- potential architectural evolution paths
- components that may need independent scaling
- constraints that may limit future growth
- intentional non-goals for MVP

This document exists to ensure:

- the current architecture is not over-engineered
- future scaling paths are acknowledged
- trade-offs are explicitly documented
- reviewers understand system limitations

This document should be read alongside:

- DOC-003 (System Architecture)
- DOC-012A (Runtime Topology)
- DOC-013 (Infrastructure & Network Topology)

---

# ⚠️ THIS IS NOT A REQUIREMENTS DOCUMENT

This document describes **potential future evolution only**.

The MVP does NOT need to implement any of these capabilities.

These notes exist to document scalability awareness, not implementation intent.

---

# Part 1: Current Architecture Constraints

## Design Constraints

| Constraint | Impact | Acceptable for MVP |
|------------|--------|---------------------|
| Single application instance | No horizontal scaling | Yes |
| Single PostgreSQL instance | No DB clustering | Yes |
| Single Redis instance | No cache cluster | Yes |
| Synchronous transactions | Limited throughput | Yes |
| Monolithic deployment | No service separation | Yes |

## Rationale

- MVP prioritises correctness over scale
- System complexity is intentionally minimized
- Vertical scaling is sufficient
- Architecture clarity > performance optimization

---

# Part 2: Potential Evolution Paths

## Application Layer

| Evolution | Trigger | Complexity |
|----------|--------|------------|
| Horizontal scaling | Increased load | Medium |
| Stateless sessions | After scaling | Medium |
| Load balancing | Multi-instance deployment | Low |

---

## Database Layer

| Evolution | Trigger | Complexity |
|-----------|--------|------------|
| Read replicas | Read-heavy workloads | Medium |
| Connection pooling tuning | Performance limits | Low |
| Partitioning | Large audit tables | Medium |
| Separate audit DB | Compliance requirements | High |

---

## Cache Layer

| Evolution | Trigger | Complexity |
|-----------|--------|------------|
| Redis cluster | Memory limits | Medium |
| Persistent cache layer | Durability needs | Medium |

---

# Part 3: Potential Service Boundaries

| Service | Responsibility | Trigger for Split |
|--------|----------------|-------------------|
| Audit Service | Audit logging | Compliance separation |
| Reconciliation Service | Consistency checks | Independent scaling |
| Notification Service | Messaging delivery | External system load |

**Note:** Microservices are NOT part of MVP scope.

---

# Part 4: Database Evolution

## Audit Growth Considerations

| Issue | Future Mitigation |
|------|-------------------|
| Unlimited audit growth | Partition by time |
| Query slowdown | Archival strategy |
| Storage limits | Separate audit DB |

## MVP Approach

- Audit logs are stored indefinitely
- No archiving is implemented

---

# Part 5: Performance Assumptions

## Expected MVP Load

| Metric | Assumption |
|--------|------------|
| Concurrent users | 1–10 |
| Transactions/sec | < 10 |
| Audit events/day | < 10,000 |

## Scaling Triggers

| Metric | Action Threshold |
|--------|------------------|
| Concurrent users | > 100 |
| Transactions/sec | > 50 |
| Audit events/day | > 100,000 |

---

# Part 6: Technology Replacement Options

| Component | Possible Replacement | Condition |
|-----------|---------------------|----------|
| PostgreSQL | Distributed SQL (CockroachDB, Yugabyte) | Horizontal scaling required |
| Redis | KeyDB / Dragonfly | Performance limits |
| Spring Boot | Quarkus / Micronaut | Memory/startup constraints |

## Lock-in Assessment

| Component | Risk | Mitigation |
|-----------|------|------------|
| PostgreSQL | Low | Standard SQL usage |
| Redis | Low | Standard client usage |
| Spring Boot | Medium | Domain logic isolation |

---

# Part 7: Evolution Non-Goals

The following are explicitly excluded:

- full microservices architecture
- event sourcing (beyond audit logs)
- CQRS
- distributed transactions (2PC/XA)
- multi-region active-active systems
- zero-downtime migration guarantees

---

# Part 8: Operational Scaling

| Area | MVP | Future |
|------|-----|--------|
| Backups | Manual/daily | Automated hourly |
| Monitoring | Minimal | Full observability stack |
| Incident response | None | Defined on-call rotation |
| Runbooks | Basic notes | Formal procedures |

---

# Part 9: Alignment

| Document | Relationship |
|----------|-------------|
| DOC-003 | Core architecture |
| DOC-012A | Deployment topology |
| DOC-013 | Network constraints |
| ADR-001 | Monolith vs microservices decision |

---

# Part 10: Glossary

| Term | Meaning |
|------|--------|
| Vertical Scaling | Increasing resources on single node |
| Horizontal Scaling | Adding more nodes |
| Read Replica | DB copy for read queries |
| Partitioning | Splitting large tables |
| Service Boundary | Logical split point for future services |

---

# Part 11: Document Control

| Field | Value |
|------|------|
| Document ID | DOC-14 |
| Version | 1.0 |
| Type | Informational (Future Planning) |
| Depends On | DOC-3, DOC-12A, DOC-13 |

---

**End of Document #14**