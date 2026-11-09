import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import {
  Activity,
  ArrowUpRight,
  Box,
  CheckCircle2,
  ChevronRight,
  Code2,
  Download,
  FileText,
  FlaskConical,
  Folder,
  Globe,
  LayoutDashboard,
  Play,
  Plus,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  Sparkles,
  Square,
  Terminal,
  TriangleAlert,
  Zap,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  CartesianGrid,
  XAxis,
  YAxis,
  Tooltip,
} from "recharts";
import "./style.css";

type Row = {
  id: string;
  name?: string;
  createdAt: string;
  status?: string;
  scenarioId?: string;
  plan?: string;
  result?: string;
  severity?: string;
  confidence?: string;
  title?: string;
  detail?: string;
  model?: string;
  document?: string;
};
let authorization = "";
async function api<T = Row[]>(path: string, body?: unknown): Promise<T> {
  const r = await fetch("/api/v1" + path, {
    method: body === undefined ? "GET" : "POST",
    headers: {
      Authorization: authorization,
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    const text = await r.text();
    let message = "Request failed (" + r.status + ")";
    try {
      message = JSON.parse(text).detail || message;
    } catch {}
    throw new Error(message);
  }
  return r.json();
}
const nav = [
  ["Overview", LayoutDashboard],
  ["Projects", Folder],
  ["Target Explorer", Globe],
  ["Autonomous Testing", Sparkles],
  ["Chaos Lab", FlaskConical],
  ["Scenarios", Code2],
  ["Executions", Activity],
  ["Findings", TriangleAlert],
  ["Reports", FileText],
  ["Settings", Settings],
] as const;
const parse = (s?: string) => {
  try {
    return JSON.parse(s || "null");
  } catch {
    return null;
  }
};
function Badge({ value }: { value?: string }) {
  return (
    <span className={"badge " + (value || "").toLowerCase()}>
      <i />
      {value || "—"}
    </span>
  );
}
function App() {
  const [logged, setLogged] = useState(false),
    [user, setUser] = useState("admin"),
    [password, setPassword] = useState(""),
    [tab, setTab] = useState("Overview"),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [selected, setSelected] = useState<Row | null>(null),
    [search, setSearch] = useState(""),
    [project, setProject] = useState(""),
    [target, setTarget] = useState(""),
    [discovery, setDiscovery] = useState("");
  const [category, setCategory] = useState("RAPID"),
    [fault, setFault] = useState("NONE"),
    [clicks, setClicks] = useState(15),
    [concurrency, setConcurrency] = useState(2),
    [delay, setDelay] = useState(300),
    [seed, setSeed] = useState(42),
    [defect, setDefect] = useState("NONE"),
    [ai, setAi] = useState(false),
    [confirmed, setConfirmed] = useState(false),
    [name, setName] = useState(""),
    [targetName, setTargetName] = useState("Local sandbox"),
    [url, setUrl] = useState(""),
    [authorized, setAuthorized] = useState(false);
  const qc = useQueryClient();
  const projects = useQuery({
    queryKey: ["projects"],
    queryFn: () => api("/projects"),
    enabled: logged,
  });
  const targets = useQuery({
    queryKey: ["targets", project],
    queryFn: () => api("/projects/" + project + "/targets"),
    enabled: logged && !!project,
  });
  const settings = useQuery({
    queryKey: ["settings"],
    queryFn: () => api<{ demoUrl: string; aiEnabled: boolean }>("/settings"),
    enabled: logged,
  });
  const scenarios = useQuery({
    queryKey: ["scenarios"],
    queryFn: () => api("/scenarios"),
    enabled: logged,
    refetchInterval: 4000,
  });
  const executions = useQuery({
    queryKey: ["executions"],
    queryFn: () => api("/executions"),
    enabled: logged,
    refetchInterval: 2000,
  });
  const findings = useQuery({
    queryKey: ["findings"],
    queryFn: () => api("/findings"),
    enabled: logged,
    refetchInterval: 4000,
  });
  const reports = useQuery({
    queryKey: ["reports"],
    queryFn: () => api("/reports"),
    enabled: logged,
  });
  const discovered = useQuery({
    queryKey: ["discovery", discovery],
    queryFn: () => api<Row>("/discovery-sessions/" + discovery),
    enabled: logged && !!discovery,
    refetchInterval: 3000,
  });
  const detail = useQuery({
    queryKey: ["execution", selected?.id],
    queryFn: () => api<Row>("/executions/" + selected?.id),
    enabled: logged && !!selected && !!selected.scenarioId,
    refetchInterval: 2000,
  });
  async function act(fn: () => Promise<unknown>) {
    setError("");
    setBusy(true);
    try {
      await fn();
      await qc.invalidateQueries();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function download(path: string, filename: string) {
    const r = await fetch("/api/v1" + path, {
      headers: { Authorization: authorization },
    });
    if (!r.ok) throw new Error("Artifact unavailable or expired");
    const u = URL.createObjectURL(await r.blob());
    const a = document.createElement("a");
    a.href = u;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(u), 1000);
  }
  async function run(scenarioId: string) {
    const e = await api<Row>("/executions", { scenarioId, defect, confirmed });
    setSelected(e);
    setTab("Executions");
  }
  const ex = executions.data || [],
    fs = findings.data || [],
    plans = scenarios.data || [];
  const active = ex.filter((e) =>
    ["QUEUED", "RUNNING"].includes(e.status || ""),
  );
  const result = parse((detail.data || selected)?.result);
  const chart = ["PASSED", "FAILED", "INCONCLUSIVE", "CANCELLED"].map(
    (status) => ({
      status: status.toLowerCase(),
      executions: ex.filter((e) => e.status === status).length,
    }),
  );
  if (!logged)
    return (
      <main className="login">
        <div className="brandmark">
          <Zap size={30} />
        </div>
        <span className="eyebrow">AUTONOMOUS EDGE CASE EXPLORER</span>
        <h1>
          ChaosMonkey<span>QA</span>
        </h1>
        <p>Break the expected. Discover the unexpected.</p>
        <form
          className="panel"
          onSubmit={(e) => {
            e.preventDefault();
            act(async () => {
              authorization = "Basic " + btoa(user + ":" + password);
              await api("/auth/login", {});
              setPassword("");
              setLogged(true);
            });
          }}
        >
          <h2>Sign in to your workspace</h2>
          <label>
            Username
            <input
              autoComplete="username"
              value={user}
              onChange={(e) => setUser(e.target.value)}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </label>
          <button className="primary" disabled={busy}>
            Sign in <ArrowUpRight size={17} />
          </button>
          {error && (
            <p role="alert" className="error">
              {error}
            </p>
          )}
        </form>
        <small>
          <ShieldCheck size={14} /> Authorized sandbox testing only
        </small>
      </main>
    );
  return (
    <div className="app">
      <aside>
        <a className="brand" href="#" onClick={() => setTab("Overview")}>
          <span className="brandmark">
            <Zap size={21} />
          </span>
          <strong>
            ChaosMonkey<em>QA</em>
          </strong>
        </a>
        <div className="workspace">
          <span className="avatar">CM</span>
          <div>
            Local workspace<small>Development environment</small>
          </div>
          <ChevronRight size={15} />
        </div>
        <span className="nav-label">WORKSPACE</span>
        <nav>
          {nav.map(([label, Icon]) => (
            <button
              key={label}
              className={tab === label ? "active" : ""}
              onClick={() => {
                setTab(label);
                setSelected(null);
                setSearch("");
              }}
            >
              <Icon size={18} />
              {label}
              {label === "Findings" && fs.length > 0 && <b>{fs.length}</b>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="sandbox">
            <ShieldCheck size={18} />
            <div>
              Safe by design<small>Isolated sandbox execution</small>
            </div>
          </div>
          <button
            onClick={() => {
              authorization = "";
              setLogged(false);
              qc.clear();
            }}
          >
            <span className="avatar">{user[0]?.toUpperCase()}</span>
            <div>
              {user}
              <small>Sign out</small>
            </div>
          </button>
        </div>
      </aside>
      <div className="main">
        <header>
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} /> <span>{tab}</span>
          </div>
          <div className="header-right">
            <span className="connection">
              <i /> Local environment
            </span>
            <button
              className="icon"
              title="Refresh data"
              onClick={() => qc.invalidateQueries()}
            >
              <RefreshCw size={17} />
            </button>
            <span className="avatar">{user[0]?.toUpperCase()}</span>
          </div>
        </header>
        <main>
          <div className="page-title">
            <div>
              <span className="eyebrow">CHAOSMONKEY QA / WORKSPACE</span>
              <h1>
                {tab}
                <span className="title-dot">.</span>
              </h1>
              <p>
                {tab === "Overview"
                  ? "Explore the edges. Find what your happy path missed."
                  : "Discover, execute, and investigate with reproducible evidence."}
              </p>
            </div>
            <button
              className="primary"
              onClick={() => {
                setTab("Autonomous Testing");
                setSelected(null);
              }}
            >
              <Plus size={17} /> New exploration
            </button>
          </div>
          {(error || executions.error || projects.error) && (
            <div className="error" role="alert">
              {error || (executions.error || projects.error)?.message}
              <button onClick={() => setError("")}>Dismiss</button>
            </div>
          )}
          {tab === "Overview" && (
            <>
              <div className="stats">
                {[
                  ["Total executions", ex.length, Activity, "Observed runs"],
                  [
                    "Saved scenarios",
                    plans.length,
                    Code2,
                    "Reproducible plans",
                  ],
                  [
                    "Confirmed defects",
                    fs.filter((f) => f.confidence === "CONFIRMED").length,
                    TriangleAlert,
                    "Evidence-backed findings",
                  ],
                  [
                    "Active explorations",
                    active.length,
                    Zap,
                    "Queued or running",
                  ],
                ].map(([label, value, Icon, caption], i) => {
                  const I = Icon as typeof Activity;
                  return (
                    <section className={"stat stat-" + i} key={String(label)}>
                      <div>
                        {String(label)}
                        <I size={18} />
                      </div>
                      <strong>{String(value)}</strong>
                      <small>{String(caption)}</small>
                    </section>
                  );
                })}
              </div>
              <div className="overview-grid">
                <section className="panel">
                  <div className="section-head">
                    <h2>Execution health</h2>
                    <span className="muted">Latest {ex.length} executions</span>
                  </div>
                  <p className="muted">Outcomes from actual sandbox runs</p>
                  <div className="chart">
                    <ResponsiveContainer width="100%" height={220}>
                      <BarChart data={chart}>
                        <CartesianGrid stroke="#262936" vertical={false} />
                        <XAxis
                          dataKey="status"
                          stroke="#83899d"
                          fontSize={12}
                        />
                        <YAxis
                          allowDecimals={false}
                          stroke="#83899d"
                          fontSize={12}
                        />
                        <Tooltip
                          contentStyle={{
                            background: "#171b28",
                            border: "1px solid #34384b",
                            borderRadius: 8,
                          }}
                        />
                        <Bar
                          dataKey="executions"
                          fill="#aa93ff"
                          radius={[5, 5, 0, 0]}
                          maxBarSize={55}
                          isAnimationActive={false}
                        />
                      </BarChart>
                    </ResponsiveContainer>
                  </div>
                </section>
                <section className="panel callout">
                  <span className="orb">
                    <FlaskConical size={36} />
                  </span>
                  <span className="eyebrow">SIGNATURE EXPLORATION</span>
                  <h2>Meet Chaos Checkout</h2>
                  <p>
                    Rapid clicks. Concurrent coupons. Lost responses. One
                    independently verified final state.
                  </p>
                  <div className="chips">
                    <span>15 rapid clicks</span>
                    <span>Bounded concurrency</span>
                  </div>
                  <button
                    className="secondary"
                    onClick={() => setTab("Chaos Lab")}
                  >
                    Open Chaos Lab <ArrowUpRight size={16} />
                  </button>
                </section>
              </div>
              <section className="panel">
                <div className="section-head">
                  <h2>
                    Recent executions <span className="count">{ex.length}</span>
                  </h2>
                  <button
                    className="text-button"
                    onClick={() => setTab("Executions")}
                  >
                    View all <ArrowUpRight size={15} />
                  </button>
                </div>
                {executionTable(ex.slice(0, 5))}
              </section>
              <div className="footer-note">
                <ShieldCheck size={14} /> Only confirmed, authorized local demo
                targets can receive test traffic.<span>RULES ENGINE · v1</span>
              </div>
            </>
          )}
          {tab === "Projects" && (
            <div className="two-col">
              <section className="panel">
                <h2>Create a project</h2>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    act(async () => {
                      const p = await api<Row>("/projects", { name });
                      setProject(p.id);
                      setName("");
                    });
                  }}
                >
                  <label>
                    Project name
                    <input
                      required
                      maxLength={120}
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      placeholder="Checkout reliability"
                    />
                  </label>
                  <button className="primary" disabled={busy}>
                    <Plus size={16} /> Create project
                  </button>
                </form>
                <h3>Your projects</h3>
                {projects.data?.map((p) => (
                  <button
                    className={"list-row " + (project === p.id ? "chosen" : "")}
                    key={p.id}
                    onClick={() => {
                      setProject(p.id);
                      setTarget("");
                    }}
                  >
                    <Folder size={17} />
                    {p.name}
                    <ChevronRight size={16} />
                  </button>
                ))}
              </section>
              <section className="panel">
                <h2>Authorize a target</h2>
                <p className="muted">
                  Choose a project, then explicitly authorize the isolated demo.
                </p>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    act(async () => {
                      const t = await api<Row>(
                        "/projects/" + project + "/targets",
                        {
                          name: targetName,
                          baseUrl: url || settings.data?.demoUrl,
                          environment: "LOCAL_DEMO",
                          authorized,
                        },
                      );
                      setTarget(t.id);
                    });
                  }}
                >
                  <label>
                    Project
                    <select
                      required
                      value={project}
                      onChange={(e) => setProject(e.target.value)}
                    >
                      <option value="">Select project</option>
                      {projects.data?.map((p) => (
                        <option key={p.id} value={p.id}>
                          {p.name}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Target name
                    <input
                      value={targetName}
                      onChange={(e) => setTargetName(e.target.value)}
                    />
                  </label>
                  <label>
                    Sandbox URL
                    <input
                      value={url || settings.data?.demoUrl || ""}
                      onChange={(e) => setUrl(e.target.value)}
                    />
                  </label>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={authorized}
                      onChange={(e) => setAuthorized(e.target.checked)}
                    />{" "}
                    I am authorized to test this isolated local demo.
                  </label>
                  <button
                    className="primary"
                    disabled={busy || !project || !authorized}
                  >
                    Save authorized target
                  </button>
                </form>
                {targets.data?.map((t) => (
                  <div className="list-row" key={t.id}>
                    <Globe size={16} />
                    {t.name}
                    <Badge value="LOCAL_DEMO" />
                  </div>
                ))}
              </section>
            </div>
          )}
          {["Autonomous Testing", "Chaos Lab"].includes(tab) && (
            <div className="two-col">
              <section className="panel">
                <h2>
                  {tab === "Chaos Lab"
                    ? "Build a chaos scenario"
                    : "Configure exploration"}
                </h2>
                <p className="muted">
                  Rules mode · fixed fixture · one browser · maximum 120 API
                  requests
                </p>
                <form
                  onSubmit={(e) => {
                    e.preventDefault();
                    act(async () => {
                      const s = await api<Row>("/scenarios/generate", {
                        targetId: target,
                        category,
                        seed,
                        clicks,
                        concurrency,
                        fault,
                        delayMs: delay,
                        ai,
                      });
                      await run(s.id);
                    });
                  }}
                >
                  {targetPicker()}
                  <div className="field-grid">
                    <label>
                      Category
                      <select
                        value={category}
                        onChange={(e) => setCategory(e.target.value)}
                      >
                        {[
                          "RAPID",
                          "CONCURRENCY",
                          "NETWORK",
                          "BOUNDARY",
                          "SESSION",
                          "CONTRACT",
                          "STATE",
                        ].map((c) => (
                          <option key={c}>{c}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Deterministic seed
                      <input
                        type="number"
                        value={seed}
                        onChange={(e) => setSeed(+e.target.value)}
                      />
                    </label>
                    <label>
                      Rapid clicks
                      <input
                        type="number"
                        min={1}
                        max={30}
                        value={clicks}
                        onChange={(e) => setClicks(+e.target.value)}
                      />
                    </label>
                    <label>
                      Concurrent actions
                      <input
                        type="number"
                        min={1}
                        max={4}
                        value={concurrency}
                        onChange={(e) => setConcurrency(+e.target.value)}
                      />
                    </label>
                    <label>
                      Fault injection
                      <select
                        value={fault}
                        onChange={(e) => setFault(e.target.value)}
                      >
                        {[
                          "NONE",
                          "DELAY",
                          "BLOCK",
                          "HTTP_500",
                          "HTTP_503",
                          "TIMEOUT",
                          "LOST_RESPONSE",
                          "OFFLINE",
                        ].map((f) => (
                          <option key={f}>{f}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      Delay (ms)
                      <input
                        type="number"
                        min={0}
                        max={3000}
                        value={delay}
                        onChange={(e) => setDelay(+e.target.value)}
                      />
                    </label>
                  </div>
                  <label>
                    Demo defect mode
                    <select
                      value={defect}
                      onChange={(e) => setDefect(e.target.value)}
                    >
                      {[
                        "NONE",
                        "DUPLICATE_ORDER",
                        "DOUBLE_COUPON",
                        "INCORRECT_TOTAL",
                        "INVENTORY_RACE",
                        "MISSING_IDEMPOTENCY",
                        "TIMEOUT_INCONSISTENCY",
                      ].map((d) => (
                        <option key={d}>{d}</option>
                      ))}
                    </select>
                  </label>
                  <p className="muted">
                    Defect modes require DEMO_ALLOW_DEFECTS=true on the demo
                    service.
                  </p>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={ai}
                      disabled={!settings.data?.aiEnabled}
                      onChange={(e) => setAi(e.target.checked)}
                    />{" "}
                    AI suggestion: one call, up to 256 output tokens.{" "}
                    {settings.data?.aiEnabled
                      ? "Provider configured"
                      : "Configure provider in environment to enable"}
                  </label>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={confirmed}
                      onChange={(e) => setConfirmed(e.target.checked)}
                    />{" "}
                    I confirm state-changing tests against this sandbox fixture.
                  </label>
                  <button
                    className="primary"
                    disabled={busy || !target || !confirmed}
                  >
                    <Play size={16} /> Generate & execute
                  </button>
                </form>
              </section>
              <section className="panel">
                <div className="section-head">
                  <h2>Verification plan</h2>
                  <Badge value="BOUNDED" />
                </div>
                <div className="workflow">
                  {[
                    "Create isolated cart",
                    "Discover checkout controls",
                    "Overlap coupon requests",
                    "Submit & inject selected fault",
                    "Retry with same idempotency key",
                    "Inspect independent final state",
                  ].map((s, i) => (
                    <div key={s}>
                      <span>{i + 1}</span>
                      {s}
                    </div>
                  ))}
                </div>
                <h3>Business invariants</h3>
                {[
                  "One order and one mock payment capture",
                  "Correct final total: $90.00",
                  "Exactly one coupon application",
                  "Inventory conserved: 99 remaining",
                  "UI and API state agree",
                ].map((s) => (
                  <p className="invariant" key={s}>
                    <CheckCircle2 size={16} />
                    {s}
                  </p>
                ))}
                <p className="muted">
                  Replay preserves actions and the selected fault. Concurrent
                  race outcomes can vary.
                </p>
              </section>
            </div>
          )}
          {tab === "Target Explorer" && (
            <>
              <section className="panel">
                <h2>Discover an authorized target</h2>
                {targetPicker()}
                <button
                  className="primary"
                  disabled={!target || busy}
                  onClick={() =>
                    act(async () => {
                      const d = await api<Row>("/discovery-sessions", {
                        targetId: target,
                      });
                      setDiscovery(d.id);
                    })
                  }
                >
                  <Search size={16} /> Start discovery
                </button>
              </section>
              {discovered.data && (
                <section className="panel">
                  <div className="section-head">
                    <h2>Application model</h2>
                    <Badge value={discovered.data.status} />
                  </div>
                  {parse(discovered.data.model)?.pages?.map((p: any) => (
                    <div key={p.id}>
                      <h3>{p.title}</h3>
                      <p className="muted">{p.url}</p>
                      <div className="table-wrap">
                        <table>
                          <thead>
                            <tr>
                              <th>Element</th>
                              <th>Role</th>
                              <th>Locator candidates</th>
                              <th>Confidence</th>
                            </tr>
                          </thead>
                          <tbody>
                            {p.elements.map((e: any) => (
                              <tr key={e.id}>
                                <td>{e.name || "Unnamed"}</td>
                                <td>{e.role}</td>
                                <td>
                                  <code>{e.locators.join(" / ")}</code>
                                </td>
                                <td>{Math.round(e.confidence * 100)}%</td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  ))}
                  <pre>
                    {JSON.stringify(
                      parse(discovered.data.model)?.endpoints,
                      null,
                      2,
                    )}
                  </pre>
                </section>
              )}
            </>
          )}
          {tab === "Scenarios" && (
            <section className="panel">
              <h2>Saved scenario definitions</h2>
              <p className="muted">
                Replay with the original seed and a fresh fixture. Confirm
                execution in Chaos Lab first.
              </p>
              {plans.length === 0
                ? empty(
                    "No saved scenarios",
                    "Generate your first plan in Autonomous Testing.",
                  )
                : plans.map((s) => {
                    const p = parse(s.plan);
                    return (
                      <div className="scenario" key={s.id}>
                        <Code2 size={20} />
                        <div>
                          <strong>{p?.title}</strong>
                          <small>
                            Seed {p?.seed} · {p?.clicks} clicks · {p?.fault} ·{" "}
                            {p?.risk} risk
                          </small>
                        </div>
                        <button
                          disabled={!confirmed || busy}
                          onClick={() => act(() => run(s.id))}
                        >
                          <Play size={15} /> Replay
                        </button>
                        <button
                          onClick={() => {
                            const a = document.createElement("a");
                            a.href = URL.createObjectURL(
                              new Blob([s.plan || ""], {
                                type: "application/json",
                              }),
                            );
                            a.download = "scenario.json";
                            a.click();
                            URL.revokeObjectURL(a.href);
                          }}
                        >
                          <Download size={15} />
                        </button>
                      </div>
                    );
                  })}
            </section>
          )}
          {tab === "Executions" && (
            <>
              <section className="panel">
                <div className="section-head">
                  <h2>Execution history</h2>
                  <span className="muted">Updates every 2 seconds</span>
                </div>
                {executionTable(ex)}
              </section>
              {selected && (
                <section className="panel">
                  <div className="section-head">
                    <h2>Execution {selected.id.slice(0, 8)}</h2>
                    <div className="actions">
                      <Badge value={detail.data?.status || selected.status} />
                      <button
                        onClick={() =>
                          act(() =>
                            api("/executions/" + selected.id + "/cancel", {}),
                          )
                        }
                      >
                        <Square size={13} /> Stop
                      </button>
                      <button
                        onClick={() =>
                          act(async () => {
                            await api("/reports", { executionId: selected.id });
                            setTab("Reports");
                            setSelected(null);
                          })
                        }
                      >
                        <FileText size={14} /> Report
                      </button>
                    </div>
                  </div>
                  {result ? (
                    <>
                      <h3>Business assertions</h3>
                      {result.assertions.map((c: any, i: number) => (
                        <div className="assertion" key={i}>
                          <Badge value={c.passed ? "PASSED" : "FAILED"} />
                          <span>{c.invariant}</span>
                          <small>
                            Expected {String(c.expected)} · Actual{" "}
                            {typeof c.actual === "object"
                              ? "structured state"
                              : String(c.actual)}
                          </small>
                        </div>
                      ))}
                      <h3>Evidence</h3>
                      <div className="actions">
                        {Object.entries(result.artifacts).map(
                          ([kind, file]) => (
                            <button
                              key={kind}
                              onClick={() =>
                                act(() =>
                                  download(
                                    "/executions/" +
                                      selected.id +
                                      "/evidence/" +
                                      file,
                                    String(file),
                                  ),
                                )
                              }
                            >
                              <Download size={15} />
                              {kind}
                            </button>
                          ),
                        )}
                      </div>
                      <h3>Execution timeline</h3>
                      <div className="timeline">
                        {result.timeline.map((e: any, i: number) => (
                          <div key={i}>
                            <time>{new Date(e.at).toLocaleTimeString()}</time>
                            <Badge value={e.kind} />
                            <span>{e.message}</span>
                          </div>
                        ))}
                      </div>
                    </>
                  ) : (
                    empty(
                      "Execution in progress",
                      "The worker will publish collected evidence when the run completes.",
                    )
                  )}
                </section>
              )}
            </>
          )}
          {tab === "Findings" && (
            <section className="panel">
              <div className="section-head">
                <h2>Evidence-backed findings</h2>
                <label className="search">
                  <Search size={16} />
                  <input
                    placeholder="Search title, severity, confidence"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                </label>
              </div>
              {fs.length === 0
                ? empty(
                    "No findings yet",
                    "Run a checkout exploration to collect and analyze evidence.",
                  )
                : fs
                    .filter((f) =>
                      (f.title + " " + f.severity + " " + f.confidence)
                        .toLowerCase()
                        .includes(search.toLowerCase()),
                    )
                    .map((f) => (
                      <details key={f.id} className="finding">
                        <summary>
                          <TriangleAlert size={18} />
                          <strong>{f.title}</strong>
                          <Badge value={f.severity} />
                          <Badge value={f.confidence} />
                        </summary>
                        <p>Expected: {parse(f.detail)?.expected}</p>
                        <p>Actual: {parse(f.detail)?.actual}</p>
                        <p className="muted">
                          Endpoint: {parse(f.detail)?.endpoint}
                        </p>
                        <ol>
                          {parse(f.detail)?.reproduction?.map(
                            (s: string, i: number) => (
                              <li key={i}>{s}</li>
                            ),
                          )}
                        </ol>
                        <button
                          onClick={() => {
                            setSelected(
                              ex.find((e) => e.id === (f as any).executionId) ||
                                null,
                            );
                            setTab("Executions");
                          }}
                        >
                          Inspect execution <ArrowUpRight size={15} />
                        </button>
                      </details>
                    ))}
            </section>
          )}
          {tab === "Reports" && (
            <section className="panel">
              <h2>Execution reports</h2>
              {!reports.data?.length
                ? empty(
                    "No reports generated",
                    "Open a completed execution and choose Report.",
                  )
                : reports.data.map((r) => (
                    <div className="list-row" key={r.id}>
                      <FileText size={20} />
                      <div>
                        Evidence report
                        <small>{new Date(r.createdAt).toLocaleString()}</small>
                      </div>
                      <button
                        onClick={() =>
                          act(() => download("/reports/" + r.id, "report.json"))
                        }
                      >
                        JSON <Download size={15} />
                      </button>
                      <button
                        onClick={() =>
                          act(() =>
                            download(
                              "/reports/" + r.id + "?format=html",
                              "report.html",
                            ),
                          )
                        }
                      >
                        HTML <Download size={15} />
                      </button>
                    </div>
                  ))}
            </section>
          )}
          {tab === "Settings" && (
            <section className="panel">
              <h2>Execution policy</h2>
              <div className="setting">
                <span>Target policy</span>
                <Badge value="LOCAL_DEMO" />
              </div>
              <div className="setting">
                <span>Maximum concurrent actions</span>
                <strong>4</strong>
              </div>
              <div className="setting">
                <span>API request budget</span>
                <strong>120 per execution</strong>
              </div>
              <div className="setting">
                <span>Evidence retention</span>
                <strong>7 days</strong>
              </div>
              <div className="setting">
                <span>Scenario generation</span>
                <strong>
                  {settings.data?.aiEnabled
                    ? "AI available with rules fallback"
                    : "Deterministic rules"}
                </strong>
              </div>
              <p className="muted">
                Optional LangChain4j suggestions use a configured
                OpenAI-compatible endpoint, including Ollama. Rules mode works
                without a key. External targets and real payments are disabled.
              </p>
            </section>
          )}
        </main>
      </div>
    </div>
  );
  function targetPicker() {
    return (
      <div className="field-grid">
        <label>
          Project
          <select
            required
            value={project}
            onChange={(e) => {
              setProject(e.target.value);
              setTarget("");
            }}
          >
            <option value="">Select project</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Target environment
          <select
            required
            value={target}
            onChange={(e) => setTarget(e.target.value)}
          >
            <option value="">Select target</option>
            {targets.data?.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
      </div>
    );
  }
  function executionTable(rows: Row[]) {
    return rows.length === 0 ? (
      empty(
        "Your next discovery starts here",
        "Create a project, authorize the demo, and launch an exploration.",
      )
    ) : (
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Execution / scenario</th>
              <th>Status</th>
              <th>Started</th>
              <th>Findings</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((e) => (
              <tr key={e.id}>
                <td>
                  <button
                    className="table-link"
                    onClick={() => {
                      setSelected(e);
                      setTab("Executions");
                    }}
                  >
                    <span className="table-icon">
                      <Terminal size={16} />
                    </span>
                    <div>
                      {parse(plans.find((s) => s.id === e.scenarioId)?.plan)
                        ?.title || "Checkout exploration"}
                      <small>{e.id.slice(0, 8)}</small>
                    </div>
                  </button>
                </td>
                <td>
                  <Badge value={e.status} />
                </td>
                <td className="muted">
                  {new Date(e.createdAt).toLocaleString()}
                </td>
                <td>{parse(e.result)?.findings?.length ?? "—"}</td>
                <td>
                  <button
                    className="icon"
                    aria-label="Inspect execution"
                    onClick={() => {
                      setSelected(e);
                      setTab("Executions");
                    }}
                  >
                    <ArrowUpRight size={16} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }
}
function empty(title: string, text: string) {
  return (
    <div className="empty">
      <Box size={27} />
      <strong>{title}</strong>
      <p>{text}</p>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: 1 } } })}
    >
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
);
