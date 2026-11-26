# AI Database Agent — Project Details

## 1. Project summary

AI Database Agent allows a user to connect a PostgreSQL, MySQL, or Oracle database and interact with it using ordinary language. The application converts a user request into a structured SQL plan, validates the generated SQL, executes safe reads, and asks for explicit confirmation before executing a data modification.

Example requests:

- `Show the 10 most recent orders.`
- `Count customers by country.`
- `Change customer 42's status to ACTIVE.`
- `Delete the expired invitation with ID 100.`

The LLM is a planning component only. It never receives a database connection and cannot execute SQL directly.

## 2. Main workflow

```text
User connects a database
        |
        v
API validates the target and creates a short-lived connection pool
        |
        v
JDBC metadata is converted into structured schema information
        |
        v
User asks a natural-language question
        |
        v
LLM returns a structured SqlPlan
        |
        v
SQL parser and policy layer validate the plan
        |
        +---- SELECT --------------------> Execute with limits -> Return table
        |
        +---- INSERT/UPDATE/DELETE ------> Return operation ID -> User confirms
                                                             -> Execute once
```

## 3. Technology stack

| Layer | Technology |
|---|---|
| Web client | React 19, Vite 8, JavaScript |
| API | Java 17, Spring Boot 3.5 |
| AI integration | Spring AI 1.0, OpenAI-compatible chat API |
| Current default provider | NVIDIA OpenAI-compatible endpoint |
| SQL parsing | JSqlParser 5.3 |
| Database access | Spring JDBC, named parameters, HikariCP |
| Database drivers | PostgreSQL, MySQL, Oracle JDBC |
| Operational endpoints | Spring Boot Actuator |
| Packaging | Maven, npm, Docker multi-stage build, Docker Compose |

## 4. Features

### Database connections

- PostgreSQL, MySQL, and Oracle support.
- Database-specific JDBC URLs and drivers.
- Optional schema/owner filter.
- TLS option passed to the database driver.
- Connection target allowlist to restrict outbound database access.
- Short-lived HikariCP connection pool per user session.
- Automatic pool closure when a session expires or disconnects.

### Schema discovery

- Discovers visible tables through JDBC metadata.
- Returns table schema/name and structured column information.
- Includes data types, nullability, primary keys, and foreign-key targets.
- Produces a compact schema summary for the LLM prompt.
- Supports schema refresh from the web interface.
- Limits discovery to 200 tables and 200 columns per table.

### AI planning

- Converts natural-language requests into a structured `SqlPlan`.
- Uses the connected database dialect and discovered schema.
- Includes recent conversation context.
- Uses temperature `0.0` for more deterministic SQL generation.
- Requires the model to return JSON rather than free-form executable instructions.
- Requires user-provided values to be represented as named SQL parameters.

### SQL safety

- Treats all model output as untrusted.
- Parses SQL into an abstract syntax tree.
- Permits exactly one SQL statement.
- Verifies the actual statement type against the declared intent.
- Verifies that the generated dialect matches the connected database.
- Rejects DDL, stored procedures, locking reads, file operations, and unsupported commands.
- Rejects missing or unexpected named parameters.
- Caps SQL text at 20,000 characters.
- Limits query output to 100 rows.
- Applies a configurable query timeout, 30 seconds by default.

### Write confirmation

- `INSERT`, `UPDATE`, and `DELETE` always require confirmation.
- Each pending write receives a unique operation ID.
- The operation ID expires after 10 minutes by default.
- A confirmation executes only the SQL plan stored under that exact ID.
- Confirmation is one-time: cancel, success, failure, or expiry consumes the operation.
- A session cannot create another write while one is waiting for confirmation.

### User interface

- Database connection form.
- Connection and session status.
- Schema/table sidebar.
- Natural-language chat interface.
- Suggested questions.
- Loading, error, success, and notification states.
- SQL preview and copy action.
- Tabular result display.
- Explicit confirm/cancel controls for modifications.
- Schema refresh, chat clearing, and database disconnect actions.

## 5. Capabilities and boundaries

| Capability | Current status |
|---|---|
| Natural language to `SELECT` | Supported |
| Execute bounded read queries | Supported |
| Natural language to `INSERT` | Supported with confirmation |
| Natural language to `UPDATE` | Supported with confirmation |
| Natural language to `DELETE` | Supported with confirmation |
| Named SQL parameters | Supported |
| PostgreSQL | Supported |
| MySQL | Supported |
| Oracle | Supported |
| DDL (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`) | Blocked |
| Stored procedures and vendor commands | Blocked |
| Durable sessions across restarts | Not supported; sessions are in memory |
| Multiple API instances | Not supported without shared session design/sticky routing |
| User authentication and roles | Not implemented yet |
| Durable audit history | Not implemented yet |
| Public internet exposure | Not recommended until authentication and rate limiting are added |

## 6. REST API

Base URL:

```text
http://localhost:8083/api/v1
```

Content type for request bodies:

```text
Content-Type: application/json
```

### Endpoint summary

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/connections` | Connect a database and create a session |
| `GET` | `/sessions/{sessionId}/schema` | Refresh and return schema metadata |
| `DELETE` | `/sessions/{sessionId}` | Disconnect and close the session pool |
| `POST` | `/chat` | Convert a natural-language request into a safe database action |
| `POST` | `/operations/confirm` | Confirm or cancel a pending write operation |
| `DELETE` | `/sessions/{sessionId}/history` | Clear server-side history and cancel any pending operation |
| `GET` | `/actuator/health` | Application health status |
| `GET` | `/actuator/info` | Application information endpoint |

## 7. Endpoint details

### 7.1 Connect a database

```http
POST /api/v1/connections
```

Request:

```json
{
  "dbType": "POSTGRES",
  "host": "localhost",
  "port": 5432,
  "database": "shop",
  "username": "app_user",
  "password": "database-password",
  "schema": "public",
  "ssl": false
}
```

`dbType` must be one of `POSTGRES`, `MYSQL`, or `ORACLE`. `schema` is optional. Port must be between 1 and 65535.

Successful response:

```json
{
  "success": true,
  "sessionId": "a7fd56ef-bf25-4203-a3ea-0f84b5a0cb1b",
  "dbType": "PostgreSQL",
  "dbVersion": "17.2",
  "message": "Connected successfully. Ask your database a question.",
  "schema": {
    "tables": [
      {
        "schema": "public",
        "name": "customers",
        "columns": [
          {
            "name": "id",
            "dataType": "int8",
            "nullable": false,
            "primaryKey": true,
            "foreignKeyReference": null
          }
        ]
      }
    ]
  }
}
```

Important errors:

- `400 VALIDATION_ERROR` — missing or invalid request fields.
- `400 DATABASE_CONNECTION_FAILED` — credentials, host, database, or TLS settings failed.
- `403 CONNECTION_TARGET_BLOCKED` — host is not in the configured allowlist.
- `422 SCHEMA_INTROSPECTION_FAILED` — connection succeeded but schema metadata could not be read.

### 7.2 Refresh schema

```http
GET /api/v1/sessions/{sessionId}/schema
```

Successful response:

```json
{
  "schema": {
    "tables": []
  }
}
```

The endpoint reads live JDBC metadata again and updates the schema used by future prompts.

### 7.3 Disconnect

```http
DELETE /api/v1/sessions/{sessionId}
```

Response:

```json
{
  "success": true,
  "message": "Session closed."
}
```

The operation is idempotent. Repeating it returns success with `Session was already closed.`

### 7.4 Send a chat request

```http
POST /api/v1/chat
```

Request:

```json
{
  "sessionId": "a7fd56ef-bf25-4203-a3ea-0f84b5a0cb1b",
  "userMessage": "Show the 10 newest customers"
}
```

Possible response types are described below.

#### Query result

```json
{
  "type": "query_result",
  "answer": "Returned 10 rows. Finds the newest customers.",
  "sqlPlan": {
    "intent": "SELECT",
    "dialect": "POSTGRES",
    "sql": "SELECT id, name, created_at FROM customers ORDER BY created_at DESC LIMIT 10",
    "params": {},
    "destructive": false,
    "requiresConfirmation": false,
    "reason": "Finds the newest customers."
  },
  "columns": ["id", "name", "created_at"],
  "rows": [
    {
      "id": 101,
      "name": "Ada",
      "created_at": "2026-08-25T10:30:00Z"
    }
  ],
  "pendingConfirmation": false,
  "operationId": null,
  "affectedRows": null,
  "errorMessage": null
}
```

#### Confirmation required

```json
{
  "type": "confirmation_required",
  "answer": "Review the generated UPDATE statement and confirm it before execution.",
  "sqlPlan": {
    "intent": "UPDATE",
    "dialect": "POSTGRES",
    "sql": "UPDATE customers SET status = :status WHERE id = :customerId",
    "params": {
      "status": "ACTIVE",
      "customerId": 42
    },
    "destructive": true,
    "requiresConfirmation": true,
    "reason": "Updates the requested customer status."
  },
  "columns": [],
  "rows": [],
  "pendingConfirmation": true,
  "operationId": "1c5ccecb-dbd7-428c-85d8-50d4f1920a5a",
  "affectedRows": null,
  "errorMessage": null
}
```

#### Plain answer or blocked request

```json
{
  "type": "answer",
  "answer": "DDL operations are disabled.",
  "sqlPlan": null,
  "columns": [],
  "rows": [],
  "pendingConfirmation": false,
  "operationId": null,
  "affectedRows": null,
  "errorMessage": null
}
```

Important statuses:

- `200` — query result, plain answer, blocked unsafe SQL, or confirmation request.
- `400` — no active session or invalid request.
- `409` — another operation is waiting for confirmation.
- `502` — the AI planning workflow failed safely.

### 7.5 Confirm or cancel a write

```http
POST /api/v1/operations/confirm
```

Confirm request:

```json
{
  "sessionId": "a7fd56ef-bf25-4203-a3ea-0f84b5a0cb1b",
  "operationId": "1c5ccecb-dbd7-428c-85d8-50d4f1920a5a",
  "confirm": true
}
```

Successful execution response:

```json
{
  "type": "execution_result",
  "answer": "UPDATE completed successfully. 1 row(s) affected.",
  "sqlPlan": {
    "intent": "UPDATE",
    "dialect": "POSTGRES",
    "sql": "UPDATE customers SET status = :status WHERE id = :customerId",
    "params": {
      "status": "ACTIVE",
      "customerId": 42
    },
    "destructive": true,
    "requiresConfirmation": true,
    "reason": "Updates the requested customer status."
  },
  "columns": [],
  "rows": [],
  "pendingConfirmation": false,
  "operationId": null,
  "affectedRows": 1,
  "errorMessage": null
}
```

Send `"confirm": false` to cancel. Cancellation does not execute SQL.

Important statuses:

- `200` — executed or cancelled.
- `400` — no active session.
- `409` — operation ID is missing, incorrect, or already consumed.
- `410` — operation expired.
- `422` — the database rejected the confirmed operation.

### 7.6 Clear conversation history

```http
DELETE /api/v1/sessions/{sessionId}/history
```

This clears the server-side prompt history and cancels any pending operation for the session.

## 8. Standard response models

### `SqlPlan`

| Field | Type | Meaning |
|---|---|---|
| `intent` | string | `SELECT`, `INSERT`, `UPDATE`, or `DELETE` for executable plans |
| `dialect` | string | `POSTGRES`, `MYSQL`, or `ORACLE` |
| `sql` | string | One parsed SQL statement with named placeholders |
| `params` | object | Values for placeholders such as `:customerId` |
| `destructive` | boolean | Indicates a data-changing operation |
| `requiresConfirmation` | boolean | Model-declared confirmation requirement; server also forces it for writes |
| `reason` | string | Short explanation of the generated plan |

### API error

Connection, validation, and general API failures use:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "host must not be blank",
  "timestamp": "2026-08-26T09:30:00Z"
}
```

Chat workflow failures use the `ChatResponse` shape with `type: "error"` and a safe `errorMessage`.

## 9. Runtime limits and defaults

| Setting | Default |
|---|---|
| API port | `8083` |
| Session lifetime after last activity | 30 minutes |
| Pending operation lifetime | 10 minutes |
| Session cleanup interval | 60 seconds |
| SQL query timeout | 30 seconds |
| Maximum returned rows | 100 |
| Maximum Hikari pool size per session | 3 |
| Minimum idle connections | 0 |
| Maximum schema tables | 200 |
| Maximum columns per table | 200 |
| Stored chat entries | 20 |
| Chat entries sent to the LLM | Most recent 6 |

## 10. Environment variables

| Variable | Required | Default/purpose |
|---|---|---|
| `SPRING_AI_OPENAI_API_KEY` | Yes | AI provider credential; never store it in source |
| `SPRING_AI_OPENAI_BASE_URL` | No | `https://integrate.api.nvidia.com` |
| `SPRING_AI_OPENAI_MODEL` | No | `meta/llama-4-maverick-17b-128e-instruct` |
| `SERVER_PORT` | No | `8083` |
| `DB_AGENT_SESSION_TTL` | No | ISO-8601 duration, default `PT30M` |
| `DB_AGENT_OPERATION_TTL` | No | ISO-8601 duration, default `PT10M` |
| `DB_AGENT_QUERY_TIMEOUT_SECONDS` | No | Default `30` |
| `DB_AGENT_ALLOWED_HOSTS` | No | Comma-separated hosts; default `localhost,127.0.0.1,::1` |

The host allowlist supports exact hosts and leading wildcard suffixes such as `*.internal.example.com`.

## 11. Running locally

Configure the required environment variable in PowerShell:

```powershell
$env:SPRING_AI_OPENAI_API_KEY = "your-provider-key"
```

Start the backend:

```powershell
cd AI-Assisted_Database_Agent
.\mvnw.cmd spring-boot:run
```

Start the frontend in another terminal:

```powershell
cd ai_db_agent_ui
npm install
npm run dev
```

Open the Vite URL, normally `http://localhost:5173`.

## 12. Running with Docker

Create `.env` from `.env.example`, supply the real provider key, start Docker Desktop, and run:

```powershell
docker compose up --build
```

The production container builds the React application, packages it into Spring Boot, runs as a non-root user, uses a read-only filesystem, and binds port `8083` to localhost by default.

## 13. Quality checks

Backend:

```powershell
cd AI-Assisted_Database_Agent
.\mvnw.cmd clean test
```

Frontend:

```powershell
cd ai_db_agent_ui
npm run lint
npm run build
```

The current automated suite contains 20 passing backend tests covering application startup, Java-time error serialization, safe connection-error classification, connection host policy, JDBC URL/TLS construction, session expiry, API response contracts, and SQL validation rules.

## 14. Security and production readiness

Already implemented:

- Environment-based secrets.
- Host allowlisting.
- SQL AST parsing and intent verification.
- Named parameters.
- Query timeouts and row limits.
- Expiring sessions and pending operations.
- One-time write confirmation.
- Sanitized client errors and reduced sensitive logging.
- Non-root, read-only container configuration.

Required before external or multi-user deployment:

- OIDC or another production authentication mechanism.
- Per-user authorization and session ownership.
- CSRF protection when cookie authentication is selected.
- API rate limiting and request-size limits.
- Durable audit records for connection, query, confirmation, and execution events.
- Centralized secret manager and key rotation.
- Network-level egress restrictions in addition to the application allowlist.
- Integration tests against real PostgreSQL, MySQL, and Oracle-compatible environments.
- Observability dashboards, alerts, and distributed request tracing.

## 15. Important source locations

| Area | Location |
|---|---|
| React application | `ai_db_agent_ui/src/App.jsx` |
| Frontend API client | `ai_db_agent_ui/src/services/api.js` |
| Connection endpoints | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/controller/ConnectionController.java` |
| Chat endpoints | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/controller/ChatController.java` |
| AI planner | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/service/AiSqlPlannerService.java` |
| Prompt rules | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/ai/PromptBuilder.java` |
| SQL policy | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/service/SqlValidationService.java` |
| SQL execution | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/service/SqlExecutionService.java` |
| Schema discovery | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/service/SchemaService.java` |
| Session lifecycle | `AI-Assisted_Database_Agent/src/main/java/com/guru/AI_Assisted_Database_Agent/service/SessionStoreService.java` |
| Configuration | `AI-Assisted_Database_Agent/src/main/resources/application.properties` |
| Production image | `Dockerfile` and `compose.yaml` |
