# Operations Runbook

## Scope

This runbook covers the agentic orchestrator and distinguishes it from the URL-shortener service it proposes. The orchestrator stores proposals and exports an approved ZIP; it does not apply generated files to a service checkout.

## Configuration and startup

- Run `docker compose up -d postgres temporal` for local development. The bundled Temporal server is development-only and keeps state in memory.
- Set `AUTH_ISSUER_URI` to a trusted OIDC issuer before starting the API. Do not disable JWT validation in deployed environments.
- Set database credentials through `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`; use a secret manager outside local development.
- Configure model provider credentials only when using the optional provider. Do not include secrets in requirements or repository snapshots.
- Start with `mvn spring-boot:run` or the packaged application. Flyway owns schema changes; keep Hibernate DDL mode at `validate`.

## Authorization and limits

- `orchestrator:write` permits run creation, replanning, and clarification submission.
- `orchestrator:approve` permits plan and artifact decisions. Audit identity is taken from the JWT subject.
- `orchestrator:read` permits run inspection and approved artifact export.
- `orchestrator:ops` permits reliability and Prometheus metrics access.
- The API request guard defaults to 120 requests per minute per direct peer IP, a 256 KiB request limit, and at most 10,000 tracked peers per process. Forwarded headers are not trusted. These limits are process-local; deploy a trusted shared gateway or distributed limiter for multiple replicas.
- Write requests must include `Content-Length`. Oversized requests return `413`; unknown-length writes return `411`; rate-limited requests return `429` with `Retry-After`.

## Health and monitoring

- Check liveness/readiness at `/actuator/health/liveness` and `/actuator/health/readiness`.
- Scrape `/actuator/prometheus` with a token carrying `orchestrator:ops`.
- Inspect run/task state, retries, audit events, and validation reports through authorized run reads. A failed final-validation task is not approval-ready.
- Reliability aggregates are diagnostic metrics, not SLOs. Define service-level objectives and alert thresholds before production deployment.

## Proposal validation and recovery

- Final validation runs static proposal checks and, by default, requires generated project builds/tests to pass in a disposable Docker container with no network, CPU/memory/process/file-descriptor caps, read-only source and dependency-cache mounts, and bounded time/output.
- Configure `orchestrator.validation.enabled=false` only for isolated tests; required validation remains fail-closed unless `orchestrator.validation.required=false` is explicitly set for a test profile.
- Docker unavailable, cache incomplete, timeout, nonzero build/test result, or static-check failure blocks the final-validation task. Inspect `validation-report.md` and task failure details before replanning.
- The validation container and temporary source workspace are removed after execution. Proposals are never applied to the orchestrator checkout. Replanning increments the plan version and discards stale proposal artifacts.

## Database and deployment

- Back up PostgreSQL before deploying migrations. Test restore procedures and migration compatibility in staging.
- Restrict PostgreSQL and Temporal ports to trusted networks; the included Compose configuration is for local development only.
- Terminate TLS at a trusted ingress, configure proxy trust explicitly, and keep database/identity/provider credentials out of source control.
- Deploy application workers separately from the API for production Temporal use, and use a supported durable Temporal cluster rather than the local dev server.
- For the generated URL-shortener service, provision PostgreSQL, Redis, OIDC, a high-entropy `RATE_LIMIT_HASH_KEY`, explicit CORS origins, and network egress controls before deployment.

## Verification commands

```powershell
.\mvnw.cmd -B test
.\mvnw.cmd -B -Pintegration verify
.\mvnw.cmd -B '-Dorchestrator.validation.enabled=true' '-Dorchestrator.validation.required=true' '-Dtest=AgenticOrchestratorApplicationTests' test
docker run --rm -v "${PWD}:/src" ghcr.io/google/osv-scanner:v2.6.0 scan source --recursive /src
```

The integration profile uses disposable PostgreSQL 17 Testcontainers. OSV-Scanner v2.6.0 resolved the current Maven dependency graph and reported no issues after the Jackson/Tomcat patch updates. It queries online package/vulnerability APIs and does not download the NVD CVE corpus. A network connection to OSV/deps.dev is required. CI uses the maintained OSV reusable workflow and fails when vulnerabilities are reported.