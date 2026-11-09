# ChaosMonkey QA

**Break the expected. Discover the unexpected.**

A Java 21 modular testing platform with an authenticated REST API, isolated Playwright worker, PostgreSQL execution history, React dashboard, and a local e-commerce fixture. The implemented vertical slice is project → authorized demo target → scenario → asynchronous execution → independent invariant checks → findings → evidence report.

## Start locally

### Windows: run without Docker

Run **Run ChaosMonkey QA** from VS Code's Run and Debug panel (F5), run `qa.java`, or use:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
```

This builds and starts the dashboard, API, background worker, and local demo. Open **http://localhost:3000**. Username: **admin**. Your generated password is the value after `chaos.admin-password=` in `data/local/credentials.properties`. No Docker, PostgreSQL, RabbitMQ, or Redis installation is required for this mode. It uses a persistent H2 database in `data/local`, Flyway migrations, and database job polling. The PostgreSQL deployment below remains available separately.

Requirements: Maven 3.9+, a JDK supporting Java 21, and Node 22+. The launcher uses the workspace's portable Java 21 runtime when present, otherwise system Java. Chromium is installed automatically when missing. Subsequent starts can use `-SkipBuild`; stop the running instance before rebuilding source changes.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1
# To demonstrate intentionally enabled defects:
powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1 -SkipBuild -EnableDefects
```

Logs: `data/local/build.log`, `web-build.log`, `demo.out.log`, `platform.out.log`, and corresponding `*.err.log` files. Saved projects, executions, evidence, and the generated password survive restarts. Keep `data/` private; it is ignored by Git.

### Docker Compose: PostgreSQL deployment

Prerequisites: Docker Desktop with Linux containers and Compose v2; approximately 4 GB of available memory. First build downloads Java, Maven, Node, and browser images.

```powershell
Copy-Item .env.example .env
# Edit .env: replace the three CHANGE_ME passwords. Use 12+ characters for admin.
docker compose up --build -d
docker compose logs -f api worker
```

Open **http://localhost:3000**. Sign in using `CHAOS_ADMIN_USER` (default `admin`) and your `CHAOS_ADMIN_PASSWORD`. The demo store is at http://localhost:8090. PostgreSQL, Redis, RabbitMQ, and backend ports are not published.

1. In **Projects**, create a project, select it, and authorize the default `http://demo:8090` target. This URL is resolved by containers, not your browser.
2. In **Target Explorer**, select the project and target, then start discovery.
3. In **Autonomous Testing** or **Chaos Lab**, select the target; keep RAPID, 15 clicks, concurrency 2, no fault, defect NONE. Confirm sandbox state changes and execute.
4. Open the execution to inspect assertions, timeline, screenshot, state JSON, replay definition, and Playwright trace.
5. Choose **Report** to save JSON and HTML exports.
6. **Scenarios** replays the saved plan with the same seed and a fresh fixture. The sandbox execution confirmation in Chaos Lab must remain selected.

### Demonstrate a real defect

Defects are disabled by default. Set `DEMO_ALLOW_DEFECTS=true` in `.env`, then:

```powershell
docker compose up -d --force-recreate demo
```

Select `DUPLICATE_ORDER` in Chaos Lab. The engine independently reads order/capture counts, total, inventory, and coupon applications. Duplicate orders produce a **CONFIRMED** finding with expected and actual state. Select `LOST_RESPONSE` to let the server process checkout while the browser receives an aborted response, then retry the original key. This is browser HTTP interception, not an operating-system network disconnection.

Other explicit demo modes: `DOUBLE_COUPON`, `INCORRECT_TOTAL`, `INVENTORY_RACE`, `MISSING_IDEMPOTENCY`, `TIMEOUT_INCONSISTENCY`. All payments are local mock captures. Every run has its own cart and stock fixture, cleaned after execution. Demo state is in memory and resets on restart.

```powershell
docker compose down
# Preserve volumes unless you intentionally want to erase execution history.
```

## Build and test

Use JDK 21 and Maven 3.9+, Node 22+.

```powershell
mvn -B -ntp verify
mvn -B -ntp -pl chaosmonkey-engine org.codehaus.mojo:exec-maven-plugin:3.6.2:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
mvn -B -ntp verify "-Dchaos.browser=true"
cd chaosmonkey-web
npm ci
npm run build
```

The default suite runs policy, transport (WireMock), scenario, authenticated REST, repository/job-claim, and real HTTP shop tests. Browser acceptance tests require the explicit property and the matching Chromium installation. The Testcontainers PostgreSQL migration test skips when Docker is absent; a skipped test is not migration verification. GitHub Actions installs Chromium, runs the browser suite, and runs PostgreSQL Testcontainers on Ubuntu.

After Compose starts, `python scripts/smoke.py` exercises project creation, target authorization, discovery, queued checkout execution, invariant verification, and report export against the actual stack. It reads operator credentials from `.env` or environment variables. The CI Compose job runs this smoke check too; the workflow has not been executed in this local session.

The Windows launcher serves the built frontend and API together on port 3000. For separate frontend development, run `npm run dev` inside `chaosmonkey-web`; its proxy expects a backend on port 8080. See [configuration](docs/configuration.md) for environment variables. Both the Windows launcher and Compose are supported startup paths.

## Modules

| Module | Responsibility |
| --- | --- |
| `chaosmonkey-api` | Basic authentication, owner-scoped projects, targets, scenarios, discovery/execution queues, cancellation, SSE, findings, evidence downloads, HTML/JSON reports, audit records |
| `chaosmonkey-engine` | DNS-pinned sandbox policy, UI crawl, OpenAPI JSON endpoint import, REST Assured fixture calls, bounded parallel actions, Playwright faults, screenshots/traces, independent assertions |
| `chaosmonkey-ai` | Seeded deterministic plans and an optional bounded LangChain4j OpenAI-compatible suggestion adapter |
| `chaosmonkey-worker` | RabbitMQ wakeups with durable database polling fallback, atomic job claims, interrupted-job recovery, evidence retention |
| `chaosmonkey-demo-app` | Catalog, carts, coupon, idempotent checkout, mock captures, orders, inventory, isolated defect modes |
| `chaosmonkey-web` | React/TypeScript/Vite, Tailwind, TanStack Query, Recharts, responsive dark dashboard |

## Scope and current limits

This release implements the local checkout slice; it does **not** complete every feature in the master specification. It must not be deployed as an internet-facing production service.

- External/staging/production execution is disabled. Only an administrator-configured **isolated demo origin** is executable. General target support needs a controlled egress proxy and broader workflow adapters.
- Scenario generation uses fixed checkout operations with adjustable budgets, categories, and fault types. Discovery identifies UI elements and network endpoints but does not yet synthesize arbitrary workflows into executable actions. OpenAPI import lists endpoints; full schema-based payload generation/response validation is pending.
- Rules mode is the default. Optional AI suggestions can be enabled after configuring a compatible provider (including Ollama). One call and 256 output tokens are allowed per generation; full input-token/cost accounting and history-based prioritization are pending. The model suggests bounded parameters; it does not autonomously execute arbitrary workflows.
- Authentication is a single environment-configured operator with owner checks. User registration, team membership, credential vault/reference CRUD, and secret-encrypted target credentials are pending. The application accepts no target passwords or payment credentials.
- PostgreSQL is the durable job source. RabbitMQ provides wakeup hints; it is safe for polling to process a job first. Redis is included for infrastructure readiness; shared rate-limit/caching use is pending. Gateway rate limiting and fixed per-execution request bounds are implemented.
- One execution runs per worker process. Worker replication is supported by atomic claims. Full session scheduling, per-step retry policies, live partial evidence persistence, and richer cancellation progress are pending. SSE streams execution status; the dashboard polls.
- Evidence expires after seven days. Per-run trace/screenshot collection is limited to local synthetic fixture data; there is no general-purpose screenshot/trace redaction for arbitrary sites. Global storage quotas, upload management, and PDF export are pending.
- Inventory race mode intentionally omits stock decrement to demonstrate the conservation assertion; it is not a general concurrency fault simulator. TIMEOUT is a browser request abort with a timeout error; it does not alter the host network.
- Reports export structured JSON and HTML with an executive summary, embedded screenshot, assertions, and reproduction evidence. Advanced server-side filtering, workflow graphs, and all-time statistics are pending. Overview statistics are computed from the latest 50 executions and 100 findings.

See [architecture](docs/architecture.md), [API](docs/api.md), [security](docs/security.md), and [verification](docs/verification.md).

