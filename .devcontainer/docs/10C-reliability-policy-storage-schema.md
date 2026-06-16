# Document #10C: Reliability Policy Storage Schema

## Purpose

This document defines the database schema and operational rules for the policy-driven reliability engine described in DOC-010A.

The platform intentionally avoids hardcoded reliability behaviour.

All:

* retry behaviour,
* backoff strategies,
* circuit breaker thresholds,
* dead-letter queue routing,
* failure classification,
* and recovery coordination

must be externally configured and dynamically evaluated through PostgreSQL-backed policy tables.

This document defines:

* reliability policy schema structure,
* runtime policy evaluation behaviour,
* operational guarantees,
* policy ownership rules,
* and reliability safety constraints.

---

# Core Architectural Principles

| Principle                      | Meaning                                                             |
| ------------------------------ | ------------------------------------------------------------------- |
| No hardcoded reliability logic | Retry and recovery behaviour must never be embedded in source code  |
| Policy-driven execution        | Reliability decisions are evaluated dynamically at runtime          |
| PostgreSQL as authority        | Reliability policies originate from a single authoritative source   |
| Fail-safe behaviour            | Missing or invalid policy must produce safe failure outcomes        |
| Deterministic recovery         | Equivalent failure conditions produce consistent handling behaviour |
| Operational auditability       | Reliability policy changes remain historically traceable            |

---

# Part 1: Retry Policy

## 1.1 Retry Policy Table

### `retry_policy`

| Column           | Type                          | Description                            |
| ---------------- | ----------------------------- | -------------------------------------- |
| id               | BIGINT PRIMARY KEY            | Surrogate key                          |
| operation_type   | VARCHAR(50) NOT NULL          | Workflow category                      |
| max_attempts     | INT NOT NULL                  | Maximum retry attempts                 |
| backoff_strategy | VARCHAR(20) NOT NULL          | Retry delay strategy                   |
| base_delay_ms    | BIGINT                        | Initial retry delay                    |
| max_delay_ms     | BIGINT                        | Maximum retry delay                    |
| jitter_enabled   | BOOLEAN NOT NULL DEFAULT TRUE | Randomized delay variance              |
| active           | BOOLEAN NOT NULL DEFAULT TRUE | Enables policy activation/deactivation |
| created_at       | TIMESTAMP NOT NULL            | Creation timestamp                     |
| updated_at       | TIMESTAMP NOT NULL            | Modification timestamp                 |

---

## 1.2 Supported Retry Strategies

| Strategy    | Behaviour                                        |
| ----------- | ------------------------------------------------ |
| FIXED       | Constant retry interval                          |
| LINEAR      | Delay increases linearly                         |
| EXPONENTIAL | Delay increases exponentially                    |
| ADAPTIVE    | Delay dynamically adjusted by runtime conditions |

---

## 1.3 Constraints

Unique constraint:

```sql id="v8t6wa"
UNIQUE(operation_type)
```

Only one active retry policy may exist per operation type.

---

# Part 2: Backoff Policy

## 2.1 Backoff Policy Table

### `backoff_policy`

| Column              | Type                                                 | Description                        |
| ------------------- | ---------------------------------------------------- | ---------------------------------- |
| id                  | BIGINT PRIMARY KEY                                   | Surrogate key                      |
| retry_policy_id     | BIGINT REFERENCES retry_policy(id) ON DELETE CASCADE | Associated retry policy            |
| multiplier          | DECIMAL(5,2)                                         | Exponential growth factor          |
| max_delay_ms        | BIGINT                                               | Maximum delay ceiling              |
| linear_increment_ms | BIGINT                                               | Increment size for LINEAR strategy |
| fixed_delay_ms      | BIGINT                                               | Delay for FIXED strategy           |
| created_at          | TIMESTAMP NOT NULL                                   | Creation timestamp                 |

---

## 2.2 Strategy Interpretation Rules

| Strategy    | Required Fields              |
| ----------- | ---------------------------- |
| EXPONENTIAL | `multiplier`, `max_delay_ms` |
| LINEAR      | `linear_increment_ms`        |
| FIXED       | `fixed_delay_ms`             |
| ADAPTIVE    | Runtime-evaluated            |

Backoff evaluation logic must validate policy completeness before execution.

Invalid policies must fail safely.

---

# Part 3: Circuit Breaker Policy

## 3.1 Circuit Breaker Policy Table

### `circuit_breaker_policy`

| Column                | Type                          | Description                         |
| --------------------- | ----------------------------- | ----------------------------------- |
| id                    | BIGINT PRIMARY KEY            | Surrogate key                       |
| operation_type        | VARCHAR(50) NOT NULL          | Protected workflow category         |
| failure_threshold     | INT NOT NULL                  | Failures required to open circuit   |
| success_threshold     | INT NOT NULL                  | Successes required to close circuit |
| timeout_ms            | BIGINT NOT NULL               | HALF_OPEN timeout duration          |
| evaluation_window_sec | INT NOT NULL                  | Sliding failure evaluation window   |
| active                | BOOLEAN NOT NULL DEFAULT TRUE | Policy activation flag              |
| created_at            | TIMESTAMP NOT NULL            | Creation timestamp                  |
| updated_at            | TIMESTAMP NOT NULL            | Modification timestamp              |

---

## 3.2 Constraints

```sql id="1b4ylo"
UNIQUE(operation_type)
```

Only one active circuit breaker policy may exist per operation type.

---

# Part 4: Dead Letter Queue (DLQ) Policy

## 4.1 DLQ Policy Table

### `dlq_policy`

| Column                 | Type                          | Description                     |
| ---------------------- | ----------------------------- | ------------------------------- |
| id                     | BIGINT PRIMARY KEY            | Surrogate key                   |
| failure_category       | VARCHAR(50) NOT NULL          | Failure classification category |
| retention_days         | INT NOT NULL                  | Failed payload retention period |
| escalate_after_retries | INT                           | Retry count before DLQ routing  |
| alert_enabled          | BOOLEAN NOT NULL DEFAULT TRUE | Operational alert flag          |
| active                 | BOOLEAN NOT NULL DEFAULT TRUE | Policy activation flag          |
| created_at             | TIMESTAMP NOT NULL            | Creation timestamp              |

---

## 4.2 Constraints

```sql id="0aqtw6"
UNIQUE(failure_category)
```

---

# Part 5: Recovery Policy

## 5.1 Recovery Policy Table

### `recovery_policy`

| Column                    | Type                           | Description                          |
| ------------------------- | ------------------------------ | ------------------------------------ |
| id                        | BIGINT PRIMARY KEY             | Surrogate key                        |
| operation_type            | VARCHAR(50) NOT NULL           | Workflow category                    |
| restart_strategy          | VARCHAR(30) NOT NULL           | Recovery coordination strategy       |
| state_validation_required | BOOLEAN NOT NULL DEFAULT TRUE  | Validate consistency before recovery |
| replay_allowed            | BOOLEAN NOT NULL DEFAULT FALSE | Allows operation replay              |
| active                    | BOOLEAN NOT NULL DEFAULT TRUE  | Policy activation flag               |
| created_at                | TIMESTAMP NOT NULL             | Creation timestamp                   |
| updated_at                | TIMESTAMP NOT NULL             | Modification timestamp               |

---

## 5.2 Recovery Strategies

| Strategy   | Behaviour                       |
| ---------- | ------------------------------- |
| RESUME     | Continue interrupted workflow   |
| RESTART    | Restart workflow from beginning |
| COMPENSATE | Execute compensating workflow   |

Replay execution must remain idempotent and audit-safe.

---

# Part 6: Failure Classification Rules

## 6.1 Failure Classification Rule Table

### `failure_classification_rule`

| Column     | Type                          | Description                  |
| ---------- | ----------------------------- | ---------------------------- |
| id         | BIGINT PRIMARY KEY            | Surrogate key                |
| condition  | TEXT NOT NULL                 | Failure evaluation predicate |
| category   | VARCHAR(50) NOT NULL          | Mapped failure category      |
| severity   | VARCHAR(20) NOT NULL          | Operational severity         |
| action     | VARCHAR(20) NOT NULL          | Required reliability action  |
| priority   | INT NOT NULL DEFAULT 0        | Evaluation ordering          |
| active     | BOOLEAN NOT NULL DEFAULT TRUE | Rule activation flag         |
| created_at | TIMESTAMP NOT NULL            | Creation timestamp           |

---

## 6.2 Supported Severity Levels

| Severity |
| -------- |
| LOW      |
| MEDIUM   |
| HIGH     |
| CRITICAL |

---

## 6.3 Supported Reliability Actions

| Action     | Behaviour                     |
| ---------- | ----------------------------- |
| RETRY      | Retry operation               |
| ROLLBACK   | Abort and rollback            |
| DLQ        | Route to dead-letter queue    |
| COMPENSATE | Execute compensating workflow |
| IGNORE     | Record and suppress           |

---

## 6.4 Rule Evaluation Behaviour

Rules are evaluated in ascending priority order.

The first matching rule becomes authoritative for that failure event.

Evaluation flow:

```text id="r6k7fv"
Failure detected
→ Load active classification rules
→ Evaluate by priority
→ Select first matching rule
→ Execute configured reliability action
```

---

# Part 7: Runtime Evaluation Guarantees

| Guarantee                            | Meaning                                            |
| ------------------------------------ | -------------------------------------------------- |
| PostgreSQL remains authoritative     | Cache is optimization only                         |
| Missing policy fails safely          | Reliability execution must not guess behaviour     |
| Policy evaluation is deterministic   | Same failure conditions produce same outcomes      |
| Reliability actions remain auditable | Recovery decisions are historically traceable      |
| Policy activation is centralized     | Multiple conflicting active policies are forbidden |

---

# Part 8: Operational Constraints

The platform intentionally forbids:

| Forbidden Behaviour                        | Reason                              |
| ------------------------------------------ | ----------------------------------- |
| Hardcoded retry counts                     | Violates policy-driven architecture |
| Inline recovery logic                      | Reduces maintainability             |
| Multiple active policies per operation     | Causes inconsistent execution       |
| Silent retry suppression                   | Hides operational failure           |
| Cache-only policy resolution               | Risks stale behaviour               |
| Runtime policy mutation outside governance | Violates operational traceability   |

---

# Part 9: Reliability Policy Caching

Caching reliability policies is optional.

If caching is used:

| Rule                             | Requirement                                  |
| -------------------------------- | -------------------------------------------- |
| PostgreSQL remains authoritative | Cache never becomes source of truth          |
| Cache invalidation required      | Policy changes must invalidate stale entries |
| TTL externally configurable      | No hardcoded cache duration                  |
| Safe fallback required           | Cache miss re-queries PostgreSQL             |

Redis may be used as an optimization layer only.

---

# Part 10: Auditability Requirements

Reliability policy changes are operationally sensitive events.

The following actions must be auditable:

* retry policy creation,
* policy modification,
* circuit breaker threshold changes,
* DLQ retention updates,
* recovery strategy changes,
* and rule activation/deactivation.

Audit history must remain:

* append-only,
* tamper-evident,
* and historically traceable.

---

# Part 11: Alignment with Existing Documents

| Document | Relationship                                       |
| -------- | -------------------------------------------------- |
| DOC-7  | Persistence ownership and transactional guarantees |
| DOC-10A | Reliability engine behavioural architecture        |
| DOC-11A | Reliability integration testing                    |
| DOC-006  | Module ownership boundaries                        |

---

# Part 12: Document Control

| Field       | Value                                |
| ----------- | ------------------------------------ |
| Document ID | DOC-10C                             |
| Version     | 2.0                                  |
| Type        | Reliability Schema Architecture      |
| Depends On  | DOC-6, DOC-7, DOC-10A, DOC-11A |

---

**End of DOC-10C**
