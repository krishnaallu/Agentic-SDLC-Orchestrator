# Agentic Engineering Orchestrator

Java 21 / Spring Boot service that turns an engineering requirement into a durable, reviewable workflow and proposed code artifacts. The URL shortener is the sample requirement sent to the orchestrator; this project is the orchestrator, not the generated URL-shortener application.

## Implemented behavior

- Persist run inputs, scenario, plan version, dependency graph, node state, attempt counts, artifacts, human decisions, and audit events in PostgreSQL.
- Execute a durable Temporal workflow with ready DAG nodes scheduled concurrently and synchronization at dependent nodes.
- Pause before execution for plan approval; reject decisions after the decision is recorded.
- Pause ambiguous runs after the clarification node, store the human answer, then continue downstream tasks.
- Generate deterministic URL-shortener Java source, API, tests, and documentation proposals without requiring model credentials.
- Optionally use an OpenAI-compatible structured-output provider for planning and artifact generation.
- Restrict proposed files to an isolated path namespace, allowed media types, at most 12 artifacts per task, and 100,000 characters per artifact. No generated shell command is executed and no artifact is written into this repository.
- Pause after generation for a separate human artifact review. Only accepted artifacts can be exported as a ZIP; rejection marks proposals unusable and records rollback lineage.
- Use Temporal bounded retries (three attempts, exponential backoff) and persist task failure and run safe-stop state.
- Fall back to deterministic templates if the optional model provider fails or returns invalid structured content; record fallback usage in the run audit trail without persisting provider response/error bodies.
- Run static release-readiness checks for required proposal files, HTTP(S) destination validation, unsafe execution/raw-IP patterns, and artifact path boundaries; persist a validation report.
- Re-plan changed requirements with a new plan version, retain the original request and audit lineage, discard stale proposals, and require new approval.
- Reconcile pending persisted Temporal state after transient workflow-service failures.
- Provide greenfield, brownfield, and ambiguous scenario presets plus reliability metrics.

This is an assignment prototype, not production-certified software. Production use still needs security review, authentication/authorization, provider-specific integration/load testing, deployment hardening, database backup/restore testing, privacy review, and operational SLOs. Model output remains untrusted and must be human-reviewed.

## Requirements

- Java 21 or later
- Maven 3.9 or use the included Maven wrapper
- Docker Desktop with Docker Compose for PostgreSQL and Temporal local services

## Start the services

```powershell
docker compose up -d postgres temporal
mvn spring-boot:run
```

The Spring API listens on port `8080`. Temporal's development UI is on port `8233`. Health is at `/actuator/health`; Prometheus metrics are at `/actuator/prometheus`. PostgreSQL settings can be overridden with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Temporal settings can be overridden with `TEMPORAL_TARGET` and `TEMPORAL_TASK_QUEUE`.

Run tests without Docker:

```powershell
mvn test
```

The test profile uses H2 and Temporal's in-memory test server. Production-shaped local startup uses PostgreSQL migrations managed by Flyway and a Temporal dev server. Do not set Hibernate schema generation to `create` or `update` for a production profile.

## Agent provider

The deterministic template provider is the default and requires no credentials. To enable an OpenAI-compatible provider, configure environment variables before starting Spring:

```powershell
$env:ORCHESTRATOR_AGENT_PROVIDER = 'openai-compatible'
$env:ORCHESTRATOR_AGENT_API_KEY = '<set locally; do not commit>'
$env:ORCHESTRATOR_AGENT_BASE_URL = 'https://api.openai.com/v1'
$env:ORCHESTRATOR_AGENT_MODEL = 'gpt-4.1-mini'
mvn spring-boot:run
```

Use a compatible provider's base URL/model as appropriate. Credentials are not stored in the database or source. The provider must support structured JSON responses. Requests have bounded connect/read timeouts; malformed/oversized JSON and unsafe artifact paths fail closed. User requirements and brownfield context are passed as untrusted data, not system instructions.

## API walkthrough

List the three scenario examples:

```powershell
Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/v1/scenarios
```

Create a greenfield requirement run:

```powershell
$run = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/runs `
  -ContentType 'application/json' `
  -Body '{"requirement":"Build a URL shortener with expiring links, safe redirects, and click analytics","scenario":"GREENFIELD"}'
$run
```

The initial response includes a run ID, plan version, graph, and `AWAITING_APPROVAL` status. Inspect it at `GET /api/v1/runs/{runId}`. Approve or reject the plan:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/runs/$($run.id)/decision" `
  -ContentType 'application/json' `
  -Body '{"approved":true,"actor":"reviewer","note":"Plan reviewed"}'
```

The Temporal workflow then executes independent tasks concurrently. For an ambiguous run, the workflow pauses with `AWAITING_CLARIFICATION` after producing its question artifact. Submit the human answer:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/runs/$($run.id)/clarification" `
  -ContentType 'application/json' `
  -Body '{"answers":"Omitted expiry means no expiry; reject past dates; short codes are never reused.","actor":"product-owner"}'
```

After the graph runs, inspect the generated artifact content and `AWAITING_ARTIFACT_REVIEW` status. Accept or reject the proposal with `POST /api/v1/runs/{runId}/artifacts/decision`:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/runs/$($run.id)/artifacts/decision" `
  -ContentType 'application/json' `
  -Body '{"approved":true,"actor":"reviewer","note":"Reviewed proposal and tests"}'
```

Only then can the archive be retrieved:

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/runs/$($run.id)/artifacts/export" -OutFile .\approved-url-shortener.zip
```

The archive contains proposals only. This endpoint does not apply files to the orchestrator workspace or run generated code.

### Scenario inputs

- `GREENFIELD`: use the supplied URL-shortener example to create a new service.
- `BROWNFIELD`: create a run with `scenario: "BROWNFIELD"` and a bounded `codebaseContext` string describing existing modules/APIs/data flows. The service does not scan arbitrary local files.
- `AMBIGUOUS`: submit a request with an unresolved policy detail. The workflow emits clarification questions and waits for the clarification endpoint before downstream work.

Change requirements with `POST /api/v1/runs/{runId}/replan` and `{"requirement":"..."}`. The original requirement and audit trail remain; stale artifacts are discarded, plan version increments, and approval is required again. Re-planning is rejected while execution or artifact review is active.

Reliability aggregates are available at `GET /api/v1/runs/metrics/reliability`: run success rate, retries, proposal rollback count, mean failure-to-replan recovery duration, and average end-to-end latency.

## Architecture and control flow

`PlannedAgent` and `TaskExecutionAgent` are provider boundaries. The default template adapters make deterministic artifacts; optional language-model adapters return validated JSON. `OrchestrationService` owns plan validation and PostgreSQL run lineage. Temporal owns durable waits, signals, bounded activity retries, and DAG execution. Spring transactional activities write node state, outputs, artifacts, and audit events. A scheduled reconciler retries start/signal delivery from persisted state.

The DAG stages are requirement/codebase/ambiguity analysis, architecture, parallel implementation/tests/documentation, and release readiness. The two explicit human gates are plan approval and final artifact review. Ambiguous scenarios add a clarification gate. Human acceptance permits ZIP export only; actual application of code remains an explicit operator action outside this service.

## Known limitations and trade-offs

- The included deterministic template output is a credible reproducible demonstration, not a general-purpose code generator. The optional LLM provider needs a configured API key and compatible endpoint.
- The application accepts bounded user-supplied brownfield context rather than reading arbitrary repositories; no workspace mutation or shell execution is implemented.
- Task retries are bounded through Temporal activity retry options; there is no automatic code rollback because proposals are never applied. Rejecting a proposal records a logical rollback and prevents export.
- The in-process Spring worker is suitable for the local prototype. Production should deploy workers independently, add authentication/authorization, restrict Temporal/network access, and use a supported production Temporal cluster.
- Rate limiting, tenant isolation, secret redaction from provider outputs, model-specific token budgets, and real provider contract/load tests are not yet implemented.
- Generated tests are proposed and checked for presence but are not compiled or executed. Static checks are not a substitute for running the generated project in an isolated sandbox; no code execution sandbox is implemented yet.
- Reliability aggregates are computed from persisted runs and audit events. They are demonstrator metrics, not an SLO reporting system.
