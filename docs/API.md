# API contract

Base path: `/api/v1`

## Connections

- `POST /connections` — validate credentials, create a session, and return structured schema metadata.
- `DELETE /sessions/{sessionId}` — close the pool and remove the session.
- `GET /sessions/{sessionId}/schema` — return the cached structured schema.

## Agent

- `POST /chat` with `{ "sessionId": "...", "userMessage": "..." }`.
- `POST /operations/confirm` with `{ "sessionId": "...", "operationId": "...", "confirm": true }`.
- `DELETE /sessions/{sessionId}/history` — clear both browser-visible and server-side conversation history.

Chat responses have one of these `type` values:

- `answer`
- `query_result`
- `confirmation_required`
- `execution_result`
- `error`

The confirmation response contains an `operationId`. The server executes only the plan stored under that ID and consumes it whether the user confirms or cancels.

