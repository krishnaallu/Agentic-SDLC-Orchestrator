# Engineering Orchestrator

## What This App Does

This application helps turn a software request into a reviewed engineering proposal. You describe what you want built or changed; the orchestrator creates a plan, asks for approval, prepares proposed files and checks them in an isolated environment, then lets a person review the results.

**Important:** This repository is the orchestrator. The sample URL-shortener is a proposed project created by the orchestrator. It is not a separate, running URL-shortener service, and approved files are exported as a ZIP rather than installed into another project.

## What Is Implemented

| Requirement area | What the orchestrator does | Boundary to keep in mind |
|---|---|---|
| Clear code boundaries | Keeps API, application logic, agents, domain types, persistence, configuration, security, and workflow code in separate packages. | The URL-shortener's package layout is generated proposal content, not this app's runtime code. |
| Approval security | The `secure` profile checks signed JWTs and requires different scopes for reading, changing, approving, and operations. Audit decisions use the JWT subject. | By default, the `local` profile disables authentication and binds to this computer only. Never expose it to a network. |
| Request controls | Limits request size and applies a per-process, per-peer request rate limit to run APIs. | Limits are not shared across multiple app instances. |
| URL destination safety | The generated URL-shortener proposal includes checks for unsafe schemes, private and local addresses, and common metadata hostnames. | These checks are in generated code. They are not running as a URL-shortener service here; DNS-rebinding protection needs additional production design and review. |
| Brownfield changes | Accepts a bounded set of caller-supplied source files, records a caller-declared revision, and creates candidate impacted-file and compatibility-check documents. | It does not read a Git repository itself or verify the revision. The impact list and regression checklist need engineering review. |
| Safe proposal testing | Runs generated project builds and tests in a disposable Docker container with networking disabled, resource limits, read-only source and dependency-cache mounts, and bounded time/output. | The generated files are not applied to a live service or this repository. Docker and the needed Maven dependencies must be available. |
| Workflow gates | Includes plan approval, clarification when needed, implementation and test planning, integration-test, security-review, deployment-readiness, release-readiness, final-validation, and artifact-review stages. | Some review stages create evidence for a person to inspect; they are not an automated security certification. |
| Persistence and recovery | Stores runs, plans, tasks, approvals, artifacts, and audit history in PostgreSQL. Temporal coordinates durable workflow progress and retries. | The included Temporal server is for local development and is not a production deployment. |
| Verification and operations | Includes PostgreSQL integration tests, API/security tests, concurrency tests, Prometheus metrics, an OSV dependency scan, and operating guidance. | Metrics are not production SLOs. Backup/restore drills, alerting, and production deployment still need to be set up. |

The full evidence and remaining boundaries are in [Requirement Traceability](docs/traceability.md), [Architecture](docs/architecture.md), and the [Operations Runbook](docs/runbook.md).

## Start It Locally

Prerequisites: Java 21 or newer, Docker Desktop, and PowerShell. PostgreSQL and Temporal are provided by Docker Compose.

From the repository directory, start the dependencies:

```powershell
docker compose up -d postgres temporal
```

Then start Spring Boot:

```powershell
.\mvnw.cmd spring-boot:run
```

The default profile is `local`: authentication is off and the app listens only on `127.0.0.1:8080`. It is intended for local development only. Keep the terminal running while using the app. Temporal's local web UI is at `http://localhost:8233`.

Check that the app is healthy and list the available scenario examples:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
Invoke-RestMethod http://127.0.0.1:8080/api/v1/scenarios
```

To stop the app, press `Ctrl+C` in the terminal where Maven is running. Stop the supporting containers later with `docker compose down` (this keeps the PostgreSQL data volume).

## Start Secure Mode

For a deployment that uses an identity provider, set its real issuer URL and explicitly select the `secure` profile:

```powershell
$env:AUTH_ISSUER_URI = 'https://your-real-identity-provider/issuer'
.\mvnw.cmd '-Dspring-boot.run.profiles=secure' spring-boot:run
```

Secure mode requires signed JWTs. The token must contain these scopes for the corresponding actions:

- `orchestrator:write`: create or replan a run, or submit clarification.
- `orchestrator:read`: inspect runs and export approved artifacts.
- `orchestrator:approve`: approve or reject plans and artifacts.
- `orchestrator:ops`: view reliability and Prometheus metrics.

Do not use the `local` profile outside your own computer. More deployment cautions are in the [runbook](docs/runbook.md).

## Submit A Requirement

In a second PowerShell terminal, choose a scenario and submit the requirement. This example starts a new Greenfield project:

```powershell
$request = @{
  requirement = 'Build a URL shortener with expiring links, safe redirects, and click analytics'
  scenario = 'GREENFIELD'
} | ConvertTo-Json

$run = Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8080/api/v1/runs' `
  -ContentType 'application/json' `
  -Body $request

$run | Select-Object id, scenario, status, planVersion
```

Keep the run `id`; it is how you check progress later. New runs normally wait at `AWAITING_APPROVAL` before work begins.

Approve the plan in local mode:

```powershell
$runId = $run.id
$decision = @{
  approved = $true
  actor = 'local-user'
  note = 'Plan reviewed'
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8080/api/v1/runs/$runId/decision" `
  -ContentType 'application/json' `
  -Body $decision
```

In secure mode, include an `Authorization: Bearer ...` header with a token that has the required scope. The recorded actor is taken from that token, not the JSON `actor` value.

## Check Run Progress

Fetch the run whenever you want an updated status:

```powershell
$current = Invoke-RestMethod "http://127.0.0.1:8080/api/v1/runs/$runId"
$current | Select-Object id, status, failureReason
$current.tasks | Select-Object nodeKey, status, attemptCount, outputSummary | Format-Table
```

The run response also contains `artifacts` and `audit` history:

```powershell
$current.artifacts | Select-Object taskKey, path, status
$current.audit | Select-Object action, actor, happenedAt, details | Format-Table -Wrap
```

Typical run statuses:

- `AWAITING_APPROVAL`: waiting for a person to approve the plan.
- `RUNNING`: workflow stages are being processed.
- `AWAITING_CLARIFICATION`: a requirement question needs an answer.
- `AWAITING_ARTIFACT_REVIEW`: proposed files and validation evidence are ready for review.
- `COMPLETED`: artifacts were accepted; they can be exported.
- `FAILED`: a required workflow or validation stage failed. Inspect `failureReason`, tasks, and the validation report before replanning.

Each task has its own status (`PLANNED`, `RUNNING`, `SUCCEEDED`, or `FAILED`) and attempt count. The API does not currently provide a list-all-runs endpoint, so keep each run ID.

If clarification is requested, submit the answer:

```powershell
$answer = @{ answers = 'Omitted expiry means no expiry; reject past dates.'; actor = 'local-user' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8080/api/v1/runs/$runId/clarification" `
  -ContentType 'application/json' `
  -Body $answer
```

## Review And Export

When a run reaches `AWAITING_ARTIFACT_REVIEW`, inspect the artifacts and validation report. Accept or reject the proposal:

```powershell
$review = @{ approved = $true; actor = 'local-user'; note = 'Artifacts reviewed' } | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:8080/api/v1/runs/$runId/artifacts/decision" `
  -ContentType 'application/json' `
  -Body $review
```

After accepting, download the proposed files as a ZIP:

```powershell
Invoke-WebRequest `
  -Uri "http://127.0.0.1:8080/api/v1/runs/$runId/artifacts/export" `
  -OutFile '.\approved-proposal.zip'
```

Export does not install or deploy the proposal. Applying it to a real service remains a separate, reviewed step.

## Choose A Scenario

- `GREENFIELD`: propose a new system or feature. The sample is a URL shortener.
- `BROWNFIELD`: propose a change to existing code. Provide a read-only snapshot and revision so the planner has concrete context. The snapshot can contain at most 25 files and 120 KB total.
- `AMBIGUOUS`: use when important requirements are unclear. The workflow creates questions and waits for answers before continuing.

Example Brownfield request:

```powershell
$request = @{
  requirement = 'Add click analytics without changing existing redirects'
  scenario = 'BROWNFIELD'
  snapshotRevision = 'a1b2c3d'
  codebaseSnapshot = @(
    @{ path = 'src/main/java/LinkService.java'; content = 'class LinkService { /* reviewed source excerpt */ }' }
  )
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post `
  -Uri 'http://127.0.0.1:8080/api/v1/runs' `
  -ContentType 'application/json' `
  -Body $request
```

The revision is declared by the caller; the app does not verify it against Git. It checks snapshot paths and size, rejects likely secret/key files, redacts some secret-like assignments, and treats the content as untrusted text. Brownfield impact and regression checks are candidates for review, not proof that compatibility is preserved.

## Run Checks

```powershell
# Unit and API tests
.\mvnw.cmd -B test

# PostgreSQL integration tests; Docker required
.\mvnw.cmd -B -Pintegration verify

# Run Greenfield and Brownfield generated builds/tests in the isolated Docker sandbox
.\mvnw.cmd -B '-Dorchestrator.validation.enabled=true' '-Dorchestrator.validation.required=true' '-Dtest=AgenticOrchestratorApplicationTests' test

# Online dependency scan through OSV; no NVD database download
docker run --rm -v "${PWD}:/src" ghcr.io/google/osv-scanner:v2.6.0 scan source --recursive /src
```

## Further Reading

- [Requirement traceability and known boundaries](docs/traceability.md)
- [Architecture and trust boundaries](docs/architecture.md)
- [Operations, monitoring, and deployment runbook](docs/runbook.md)
- [Postman demo collection and use cases](postman/README.md)