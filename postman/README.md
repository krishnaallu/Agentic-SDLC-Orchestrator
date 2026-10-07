# Postman Demo

## Import

In Postman, choose **Import** and select:

- `Agentic-Orchestrator.postman_collection.json`
- `Agentic-Orchestrator-Local.postman_environment.json`

Select the **Agentic Orchestrator - Local** environment. Its base URL is `http://127.0.0.1:8080`.

## Start The App

From the repository root, start PostgreSQL and Temporal, then Spring Boot:

```powershell
docker compose up -d postgres temporal
.\mvnw.cmd spring-boot:run
```

The default `local` profile is unauthenticated and listens only on loopback. Do not expose it to another machine or a public network.

## Run A Demo

1. Send **Health Check** and **List Scenario Examples**.
2. Choose a use-case folder: Greenfield for a new system, Brownfield for a change to supplied existing-code excerpts, or Ambiguous for clarification behavior.
3. Send that folder's **Create** request. Its test script saves the returned ID to a collection variable.
4. Approve the plan. Poll **Get Run...** until the status changes; the workflow is asynchronous.
5. If the status becomes `AWAITING_CLARIFICATION`, send the clarification request only then.
6. When the status becomes `AWAITING_ARTIFACT_REVIEW`, inspect artifacts and validation evidence, then send the artifact decision.
7. Export the ZIP after accepting. Export contains proposed files only; it does not apply or deploy them.

The collection stores Greenfield, Brownfield, Ambiguous, and replan run IDs separately. Use the request description as the state prerequisite for each action. A rejected plan/artifact uses the same decision endpoint with `approved` set to `false`.

## Secure Profile

For secure mode, configure a real issuer and run the app with the `secure` profile. In Postman, set the environment's `accessToken` to a valid token. It must carry the scope required for each request: `orchestrator:write`, `orchestrator:read`, `orchestrator:approve`, or `orchestrator:ops`. Do not save real tokens in a shared/exported environment file.

## Endpoints Included

The collection includes health, scenarios, reliability and Prometheus metrics, create run, inspect run/task/audit/artifact state, plan approval/rejection, clarification, replan, artifact approval/rejection, and accepted-artifact ZIP export. There is no list-all-runs endpoint; each created run ID is retained in a Postman collection variable.