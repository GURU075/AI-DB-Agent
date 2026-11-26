# Architecture

## Decision

The project uses a modular-monolith architecture. A single Spring Boot service owns the transactional workflow and a React application owns presentation. This keeps deployment and debugging simple while maintaining boundaries that can be extracted later if usage requires it.

```text
Browser
  |
  v
React web client
  |
  v
/api/v1
  |
  +-- connection  -> host policy -> Hikari DataSource
  +-- schema      -> JDBC metadata -> structured schema catalog
  +-- agent       -> prompt builder -> LLM gateway -> SqlPlan
  +-- sql policy  -> parser -> intent/dialect/safety validation
  +-- confirmation-> one-time operation ID with expiry
  +-- execution   -> query timeout, row cap, transaction boundary
  +-- session     -> ownership, expiry, connection-pool cleanup
  |
  v
PostgreSQL / MySQL / Oracle
```

## Backend boundaries

- **connection** validates targets and owns JDBC URL/DataSource creation.
- **schema** converts vendor metadata into stable API DTOs and a compact prompt summary.
- **agent** is the only module that talks to the LLM.
- **sql policy** treats all model output as untrusted input.
- **execution** receives only validated plans and applies resource limits.
- **session** owns short-lived DataSources, chat context, and pending operations.
- **web/API** exposes versioned DTOs and consistent error responses.

The current Java packages will be migrated toward these feature boundaries incrementally. Keeping the migration incremental makes behavior testable while contracts are stabilized.

## Request flow

1. The user submits database connection details over TLS.
2. The API verifies the host against an explicit allowlist and tests the connection.
3. The API creates a short-lived session and returns structured schema metadata.
4. The user sends a natural-language request.
5. The LLM returns a `SqlPlan`; it never receives execution capability.
6. The SQL policy layer parses the SQL and verifies statement count, type, dialect, and safety rules.
7. Reads execute with row and time limits.
8. Writes become one-time pending operations. Confirmation must include the exact operation ID.
9. Expired/disconnected sessions close their connection pools.

## API principles

- All supported routes live under `/api/v1`.
- Responses use an explicit `type` discriminator.
- Backend field names are the source of truth and are covered by contract tests.
- Errors have stable codes; stack traces and provider/database internals are not returned.
- Schema responses are structured JSON, not presentation-formatted strings.

## Production deployment

The intended production topology is:

```text
TLS reverse proxy / load balancer
  +-- static React assets
  +-- /api/v1 -> Spring Boot
                     +-- secret manager
                     +-- telemetry backend
                     +-- approved database network
                     +-- LLM provider
```

Before public or multi-user deployment, add OIDC authentication, per-user session ownership, CSRF protection for cookie-based authentication, rate limiting, durable audit events, centralized secrets, and an outbound network policy. These are deployment requirements rather than optional enhancements.

## Testing strategy

- Unit tests for prompt construction, SQL policy, host policy, and session expiry.
- JSON contract tests for every API response type.
- Integration tests with disposable PostgreSQL/MySQL containers.
- Frontend lint, component tests, and a production build.
- End-to-end tests for connect, query, confirm, cancel, expire, and disconnect flows.

