"""Exercise the actual Compose stack. Uses only the Python standard library."""
import base64
import json
import os
import time
import urllib.request
from pathlib import Path


def main():
    env = {}
    path = Path(__file__).resolve().parent.parent / ".env"
    if path.exists():
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                env[key.strip()] = value.strip().strip("\"'")
    env.update(os.environ)
    password = env["CHAOS_ADMIN_PASSWORD"]
    user = env.get("CHAOS_ADMIN_USER", "admin")
    authorization = "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode()
    base = "http://localhost:3000/api/v1"

    def api(route, body=None):
        data = None if body is None else json.dumps(body).encode()
        request = urllib.request.Request(base + route, data=data, headers={
            "Authorization": authorization, "Content-Type": "application/json"
        })
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)

    def wait(route):
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            result = api(route)
            if result["status"] not in ("QUEUED", "RUNNING"):
                return result
            time.sleep(1)
        raise TimeoutError("Worker did not finish within the smoke-test budget")

    deadline = time.monotonic() + 180
    while True:
        try:
            settings = api("/settings")
            break
        except (OSError, ValueError):
            if time.monotonic() > deadline:
                raise
            time.sleep(3)
    project = api("/projects", {"name": "Compose smoke checkout"})
    target = api(f"/projects/{project['id']}/targets", {
        "name": "Isolated demo", "baseUrl": settings["demoUrl"],
        "environment": "LOCAL_DEMO", "authorized": True
    })
    discovery = api("/discovery-sessions", {"targetId": target["id"]})
    model = wait(f"/discovery-sessions/{discovery['id']}")
    assert model["status"] == "PASSED", model
    assert json.loads(model["model"])["pages"], "No pages discovered"
    scenario = api("/scenarios/generate", {
        "targetId": target["id"], "category": "RAPID", "seed": 42,
        "clicks": 15, "concurrency": 2, "fault": "NONE", "delayMs": 0
    })
    execution = api("/executions", {
        "scenarioId": scenario["id"], "defect": "NONE", "confirmed": True
    })
    execution = wait(f"/executions/{execution['id']}")
    assert execution["status"] == "PASSED", execution
    result = json.loads(execution["result"])
    assert all(check["passed"] for check in result["assertions"]), result
    assert "trace" in result["artifacts"], "Trace missing"
    report = api("/reports", {"executionId": execution["id"]})
    assert api(f"/reports/{report['id']}")["execution"]["id"] == execution["id"]
    print(f"Compose smoke passed: execution {execution['id']}, report {report['id']}")


if __name__ == "__main__":
    main()
