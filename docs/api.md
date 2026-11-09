# REST API

Base `/api/v1`; all endpoints require HTTP Basic except `/actuator/health`. The authenticated OpenAPI document is `/v3/api-docs` on the backend. In Compose the backend is intentionally not published; use an internal client or `docker compose exec api` for diagnostics. JSON input uses Bean Validation. Invalid input returns ProblemDetail; forbidden scope returns 403; unknown IDs return 404.

| Method / path | Body / result |
| --- | --- |
| POST `/auth/login` | Authenticated identity |
| GET, POST `/projects` | Create `{ "name": "Checkout" }` |
| GET, POST `/projects/{id}/targets` | `{ "name":"Demo", "baseUrl":"http://demo:8090", "environment":"LOCAL_DEMO", "authorized":true }` |
| POST `/discovery-sessions` | `{ "targetId":"uuid" }`; poll result ID |
| GET `/discovery-sessions/{id}` | Status and structured model JSON text |
| POST `/openapi/import` | OpenAPI JSON document; descriptive endpoint list |
| POST `/scenarios/generate` | `{ "targetId":"uuid", "category":"RAPID", "seed":42, "clicks":15, "concurrency":2, "fault":"NONE", "delayMs":0 }` |
| GET `/scenarios` | Saved plans; `page=0&size=50` |
| POST `/executions` | `{ "scenarioId":"uuid", "defect":"NONE", "confirmed":true }`; also used for replay |
| GET `/executions` | Recent runs; `page=0&size=50` |
| GET `/executions/{id}` | Current status, final result JSON text |
| POST `/executions/{id}/cancel` | Cooperative cancellation request |
| GET `/executions/{id}/events` | SSE named `execution`, approximately once per second until terminal |
| GET `/executions/{id}/evidence/{file}` | `final.png`, `trace.zip`, `state.json`, `scenario.json` |
| GET `/findings` | Paginated rows; default 100, max 200 |
| GET `/findings/{id}` | Confirmed/suspected finding and reproduction JSON |
| POST `/reports` | `{ "executionId":"uuid" }` |
| GET `/reports`, `/reports/{id}` | Saved report list / JSON; `?format=html` for escaped HTML |
| GET `/settings` | Active sandbox and fixed policy limits |

List responses are arrays. JSON-bearing entity fields (`plan`, `model`, `result`, `detail`, `document`) are serialized strings; clients parse them explicitly. General list pagination currently applies before owner filtering; single-operator mode avoids sparse pages. A future multi-user release should filter in SQL and return a page envelope.
