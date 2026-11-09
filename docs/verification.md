# Verification record

Verified locally on **9 November 2026**, Asia/Baku. The final Java run finished at **17:07**.

## Executed successfully

```text
mvn -B -ntp verify -Dchaos.browser=true -Djvm=<workspace>/.tools/jre21/jdk-21.0.13+9-jre/bin/java.exe
BUILD SUCCESS
```

Maven used the installed JDK 26 compiler with `--release 21`. All test JVMs used portable Temurin **21.0.13**. The runtime is workspace-local and ignored by Git; the system Java installation was not changed. The build packaged executable API, worker, and demo jars.

| Test suite | Passed | Skipped |
| --- | ---: | ---: |
| Engine policy, redaction, OpenAPI, WireMock transport | 8 | 0 |
| Deterministic generation and real SDK calls against WireMock provider responses | 6 | 0 |
| Authentication, owner access, target rejection, persisted atomic claims, report escaping | 7 | 0 |
| Real Chromium checkout/discovery/fault/category/defect acceptance | 20 | 0 |
| Shop HTTP validation, concurrency, idempotency, defect fixtures | 5 | 0 |
| API → persisted queue → actual worker → browser → finding → report/replay, plus live React dashboard | 2 | 0 |
| Native PostgreSQL Testcontainers migration | 0 | 1 |
| **Total** | **48** | **1** |

The full-stack test successfully applies Flyway V1 to H2 in PostgreSQL compatibility mode and validates the schema with Hibernate. It uses real authentication, browser actions, and HTTP calls. RabbitMQ is unavailable during that test, verifying the database polling fallback. It does not substitute fake dashboard responses.

```text
cd chaosmonkey-web
npm run build
tsc -b && vite build
SUCCESS
```

The live dashboard test created a project and target, queued an execution, and verified business assertions. Desktop (1440 px) and mobile (390 px) checks found no unhandled browser exceptions or horizontal page overflow. Screenshots are in `chaosmonkey-worker/target/dashboard-desktop.png` and `dashboard-mobile.png`. Actual trace, screenshot, state, and scenario artifacts were collected by execution tests.

## Not verified here

- Docker is not installed on this workstation. Compose image builds/startup, native PostgreSQL migrations, live RabbitMQ delivery, and Redis connectivity were **not run**.
- `scripts/smoke.py` and the GitHub Actions workflow are provided for a Docker-capable environment; neither was executed in this session.
- No external LLM service or Ollama daemon was contacted. The real LangChain4j SDK was verified against WireMock OpenAI-compatible responses, including unsafe parameter rejection. Real provider/model compatibility still requires an operator-configured endpoint.
- No external applications, real payment providers, or production systems were tested.

These limits prevent claiming that the full master specification or Docker acceptance criteria are complete. See the README scope section for functionality still pending.

## Desktop startup follow-up

The Windows launcher was subsequently built and run against the packaged jars using Java 21. The live dashboard returned HTTP 200, generated-credential login succeeded, and a real queued NETWORK / LOST_RESPONSE checkout completed PASSED with all seven independent invariants verified. Screenshot, state, scenario, and trace artifacts were produced. `java qa.java` also successfully detected the running application. This verifies the no-Docker desktop startup path; it does not verify Compose or native PostgreSQL.
