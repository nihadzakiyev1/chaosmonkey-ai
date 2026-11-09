# Configuration

## Windows desktop mode

Run `powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1` from the project root, or select **Run ChaosMonkey QA** in VS Code. `qa.java` is also an executable IDE launcher.

The `local` Spring profile serves the dashboard and API at `http://localhost:3000`, runs the worker in the same process, and uses an H2 file database with Flyway. The demo runs separately at `http://localhost:8090`. Both bind to loopback. Broker notifications, RabbitMQ listeners, and Redis/RabbitMQ health checks are disabled; persisted jobs are polled directly.

The first launch generates a random operator password in ignored `data/local/credentials.properties`. Username is `admin`. The launcher reuses these credentials and the local database on subsequent starts. To stop, run `scripts/stop-local.ps1`. Use `-SkipBuild` for an already-built checkout and `-EnableDefects` to opt into demonstration defects.

## PostgreSQL / Compose mode

| Variable | Default / purpose |
| --- | --- |
| `CHAOS_ADMIN_USER` | `admin` |
| `CHAOS_ADMIN_PASSWORD` | Required; at least 12 characters; BCrypt in memory |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/chaosmonkey` |
| `DATABASE_USER` | `chaosmonkey` |
| `DATABASE_PASSWORD` | Required |
| `RABBITMQ_HOST` | `localhost`; Compose uses `rabbitmq` |
| `RABBITMQ_USER` | `chaosmonkey` |
| `RABBITMQ_PASSWORD` | Required |
| `REDIS_HOST` | `localhost`; Compose uses `redis` |
| `CHAOS_DEMO_URL` | `http://localhost:8090`; Compose uses `http://demo:8090` |
| `EVIDENCE_DIRECTORY` | `./data/evidence`; shared `/data/evidence` in Compose |
| `DEMO_ALLOW_DEFECTS` | `false`; only read by demo service |
| `CHAOS_AI_BASE_URL` | Empty; optional OpenAI-compatible `/v1` base URL |
| `CHAOS_AI_API_KEY` | Empty; optional provider secret, never returned by settings |
| `CHAOS_AI_MODEL` | Empty; model identifier required to enable the dashboard AI control |

Bounds: clicks 1–30; concurrency 1–4; plan duration 5–120 seconds (generated plans use 60); delay 0–3000 ms; API requests ≤120; discovery ≤10 pages/depth 3/100 browser requests; dashboard discovery uses 5 pages/depth 2. Each worker executes one job at a time. A maximum of five queued execution jobs are accepted under normal single-operator use. This queue limit is advisory under concurrent API submissions.

Optional LLM adapter: `ScenarioGenerator.suggest(plan, policy, baseUrl, apiKey, model)`. Supply secrets through environment-backed application configuration in an embedding integration. An Ollama endpoint exposing `/v1` can be used with a nonempty local placeholder key. One call, zero retries, a 15-second provider timeout, and 256 output tokens are enforced. Set CHAOS_AI_BASE_URL, CHAOS_AI_API_KEY, and CHAOS_AI_MODEL to enable the dashboard checkbox. Leave them empty for rules-only mode. Monetary and input-token budgets remain pending; the prompt is fixed and contains no credentials or target content.

Troubleshooting:

- API exits: verify passwords and `docker compose logs api`; Hibernate validates the Flyway schema on startup.
- Execution stays QUEUED: inspect worker logs and database access. RabbitMQ outages fall back to polling.
- INCONCLUSIVE before browser actions: verify Chromium/image version matches Playwright 1.56.0, fixture permission, and worker-to-demo connectivity.
- Defect fixture returns 403: explicitly enable `DEMO_ALLOW_DEFECTS` and recreate the demo container.
- DNS-change rejection after recreating demo: recreate API and worker so they validate and pin the new sandbox IP. A changed address is intentionally rejected within a running process.
- Missing evidence: files expire after seven days; metadata remains. Ensure the worker can write the shared volume and API can read it.
- Browser crashes: allocate Docker memory/shm; avoid scaling workers beyond available resources.

