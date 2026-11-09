package dev.chaosmonkey.engine;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.net.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** DNS-pinned local fixture transport. Redirects are never followed. */
public final class SandboxClient {
    private final String base;
    private final String host;
    private final BooleanSupplier cancelled;
    private final long deadline;
    private final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
    public SandboxClient(String target, Policy policy, BooleanSupplier cancelled, long deadline) {
        policy.target(target,true,"LOCAL_DEMO");
        URI uri=URI.create(target);
        if(!"http".equals(uri.getScheme())) throw new SecurityException("Local demo transport requires HTTP on an isolated network");
        base="http://"+policy.pinnedIpv4()+":"+(uri.getPort()<0?80:uri.getPort());
        host=uri.getAuthority(); this.cancelled=cancelled; this.deadline=deadline;
    }
    public Response request(String method,String path,Object body) {
        if(cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        if(System.nanoTime()>deadline) throw new IllegalStateException("Execution deadline exceeded");
        if(requests.incrementAndGet()>120) throw new IllegalStateException("API request budget exhausted");
        if(!path.startsWith("/api/") || path.contains("..") || path.contains(":") || path.contains("?")) throw new SecurityException("Invalid sandbox path");
        var spec=RestAssured.given().baseUri(base).header("Host",host).contentType("application/json")
            .redirects().follow(false).config(RestAssured.config().httpClient(io.restassured.config.HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout",3000).setParam("http.socket.timeout",5000)));
        if(body!=null) spec.body(body);
        return spec.request(method,path);
    }
}
