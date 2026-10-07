# Architecture

## Scope

This repository contains the engineering orchestrator. Generated projects are proposals under an isolated, request-derived root such as `generated/inventory/` or `generated/url-shortener/`; they are not part of the orchestrator runtime source tree and are not copied into this repository. The built-in deterministic generator remains the URL-shortener sample; arbitrary domains require the configured OpenAI-compatible provider.

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

## URL-shortener Sample Package Tree

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

Generated files remain data in PostgreSQL until explicit approval; export creates a ZIP but never writes into the orchestrator checkout. Required generated-project builds/tests run in a disposable Docker sandbox with no network, read-only input/cache mounts, resource limits, bounded output, and cleanup. Model-backed generic projects use domain-neutral artifact checks plus the derived project namespace; URL-shortener output receives additional sample-specific security checks. These checks and tests do not prove that model output fully meets the requirement, so review the requirement, generated domain documentation, tests, and validation report. A failed model call for a non-URL-shortener requirement fails closed rather than using the URL-shortener template. Brownfield source is caller-supplied and read-only; its revision is recorded as caller-declared provenance and is not verified against Git. The impact list is a candidate inventory, not semantic analysis. The local Temporal Compose service is development-only and uses in-memory persistence.
