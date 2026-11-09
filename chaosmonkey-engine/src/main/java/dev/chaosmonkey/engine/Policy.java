package dev.chaosmonkey.engine;

import java.net.*;
import java.util.*;
import static dev.chaosmonkey.engine.Model.*;

/** Fail-closed local sandbox policy. External target execution requires a future egress gateway. */
public final class Policy {
    private final URI sandbox;
    private final Set<String> addresses;
    public Policy(String sandboxUrl) {
        sandbox = URI.create(sandboxUrl);
        if (!Set.of("http", "https").contains(sandbox.getScheme()) || sandbox.getHost() == null || sandbox.getUserInfo() != null
                || sandbox.getQuery() != null || sandbox.getFragment() != null || !Set.of("", "/").contains(sandbox.getPath()))
            throw new IllegalArgumentException("Sandbox must be an HTTP origin");
        addresses = resolve(sandbox.getHost());
    }
    private Set<String> resolve(String host) {
        try { Set<String> ips = new TreeSet<>(); for (InetAddress a : InetAddress.getAllByName(host)) ips.add(a.getHostAddress()); return ips; }
        catch (UnknownHostException e) { throw new IllegalArgumentException("Target DNS resolution failed", e); }
    }
    public void target(String url, boolean authorized, String environment) {
        if (!authorized || !"LOCAL_DEMO".equals(environment)) throw new SecurityException("Explicit local-demo authorization required; external execution disabled");
        URI u = URI.create(url);
        if (!sameOrigin(u) || u.getUserInfo() != null || u.getFragment() != null || u.getQuery() != null
                || !Set.of("", "/").contains(u.getPath())) throw new SecurityException("Target is outside the configured sandbox origin");
        if (!addresses.equals(resolve(sandbox.getHost()))) throw new SecurityException("Target DNS changed; restart after verifying sandbox");
    }
    public boolean sameOrigin(URI uri) {
        return Objects.equals(sandbox.getScheme(), uri.getScheme()) && Objects.equals(sandbox.getHost(), uri.getHost()) && port(sandbox) == port(uri);
    }
    private int port(URI u) { return u.getPort() == -1 ? ("https".equals(u.getScheme()) ? 443 : 80) : u.getPort(); }
    public String chromiumResolverRules() {
        return "MAP " + sandbox.getHost() + " " + pinnedIpv4();
    }
    public String pinnedIpv4() { return addresses.stream().filter(a -> !a.contains(":")).findFirst().orElseThrow(() -> new SecurityException("IPv4 sandbox required")); }
    public void validate(Plan p) {
        if (p == null || p.category() == null || p.fault() == null || p.id() == null || p.title() == null
                || p.clicks() < 1 || p.clicks() > 30 || p.concurrency() < 1 || p.concurrency() > 4
                || p.timeoutSeconds() < 5 || p.timeoutSeconds() > 120 || p.delayMs() < 0 || p.delayMs() > 3000
                || p.steps() == null || p.steps().size() > 40 || p.invariants() == null)
            throw new IllegalArgumentException("Scenario exceeds deterministic execution policy");
        // The plan contains descriptions only; execution uses the closed category/fault enums.
    }
}
