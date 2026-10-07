# Architecture

## Scope

This repository contains the engineering orchestrator. A URL-shortener project is generated as a proposal under the isolated artifact root `generated/url-shortener/`; it is not part of the orchestrator runtime source tree and is not copied into this repository. The generated proposal has its own package structure, described below.

## Orchestrator package tree

```text
com.example.orchestrator
  AgenticOrchestratorApplication.java      Spring Boot bootstrap
  api/                                     REST controllers and request/response contracts
  application/                             Use cases, agent ports, domain events, artifact drafts
  domain/                                  Scenario, run/task statuses, task graph value objects
  persistence/                             JPA entities, repositories, audit and artifact storage
  agent/                                   Deterministic and language-model adapters
  workflow/                                Temporal workflow, activities, validation, coordination
  configuration/                           Runtime configuration properties
  security/                                API rate and request-size guard
```

Test sources mirror the production concerns under `src/test/java/com/example/orchestrator/`: application/API persistence tests, agent contract tests, Temporal workflow tests, request-guard tests, and opt-in PostgreSQL Testcontainers integration tests. Flyway SQL is under `src/main/resources/db/migration/`.

## Dependency direction

```text
api -> application -> domain
                 |-> persistence
                 |-> agent ports
workflow -> application + persistence + domain
agent adapters -> application ports + domain
configuration -> framework wiring only
```

The domain package has no dependency on Spring MVC, Temporal, or persistence. Application use cases orchestrate domain operations and depend on repository/agent abstractions. JPA entities and Temporal/model integrations are adapters at the edge.

## Generated URL-shortener package tree

```text
com.example.urlshortener
  UrlShortenerApplication.java
  api/LinkController.java                  HTTP contracts and routing
  service/LinkService.java                 creation, redirect, expiry, analytics
  domain/Link.java                         persisted link/domain state
  persistence/LinkRepository.java          atomic code insert and row-locked redirect lookup
  security/DestinationPolicy.java          normalized HTTP(S) and public-address policy
  security/RateLimitFilter.java            Redis quotas and request-size controls
  config/SecurityConfiguration.java        JWT scopes, CORS, and response headers
src/main/resources/db/migration/           PostgreSQL schema
src/test/java/com/example/urlshortener/    service, security, and API tests
```

Generated dependency direction is `api -> service -> domain`; service uses persistence and destination-policy interfaces/components; config wires framework security and environment settings. The domain entity does not depend on web controllers.

## Workflow stages and gates

A run has explicit graph nodes for requirement/codebase/ambiguity analysis, architecture, implementation, unit-test proposal, integration-test proposal, security review, documentation, deployment readiness, release readiness, and final validation. Implementation, unit-test planning, and documentation can proceed in parallel after architecture; integration and security work synchronize before deployment readiness. Plan approval gates proposal generation, ambiguous runs pause for clarification, and artifact approval gates ZIP export. Final validation fails closed when required isolated build/test execution is skipped or fails.

Temporal persists waits, signals, concurrent task execution, bounded retries, and failure state. PostgreSQL persists inputs, plan versions, graph state, outputs, artifacts, decisions, and audit events. The scheduled reconciler replays committed approval/clarification state if Temporal was briefly unavailable.

## Trust boundaries and limitations

Generated files remain data in PostgreSQL until explicit approval; export creates a ZIP but never writes into the orchestrator checkout. Required generated-project builds/tests run in a disposable Docker sandbox with no network, read-only input/cache mounts, resource limits, bounded output, and cleanup. Brownfield source is caller-supplied and read-only; its revision is recorded as caller-declared provenance and is not verified against Git. The impact list is a candidate inventory, not semantic analysis. Destination DNS checks reduce private-network redirect risk but cannot guarantee that a browser's later DNS lookup resolves to the same address; a production redirect service needs an explicit hostname allowlist or another DNS-rebinding-resistant redirect design and independent security review. Orchestrator rate limits are per process, not distributed. The local Temporal Compose service is development-only and uses in-memory persistence.
