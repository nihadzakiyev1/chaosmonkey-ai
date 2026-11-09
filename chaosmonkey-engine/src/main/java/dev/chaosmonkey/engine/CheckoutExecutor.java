package dev.chaosmonkey.engine;

import com.fasterxml.jackson.databind.*;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static dev.chaosmonkey.engine.Model.*;

public final class CheckoutExecutor {
    private final Policy policy;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    public CheckoutExecutor(Policy policy) { this.policy=policy; }
    public Result execute(String target,Plan plan,String defect,Path directory,BooleanSupplier cancelled) {
        policy.target(target,true,"LOCAL_DEMO"); policy.validate(plan);
        List<Event> events=new CopyOnWriteArrayList<>(); List<Check> checks=new ArrayList<>(); List<Finding> findings=new ArrayList<>(); Map<String,String> artifacts=new LinkedHashMap<>();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(plan.timeoutSeconds());
        SandboxClient api=new SandboxClient(target,policy,cancelled,deadline);
        String fixture=null;
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("scenario.json"),json.writeValueAsString(plan)); artifacts.put("scenario","scenario.json");
            var created=api.request("POST","/api/fixtures",Map.of("defect",defect));
            if(created.statusCode()!=200) throw new IllegalStateException("Demo fixture creation returned HTTP "+created.statusCode()+". Check defect-mode permission.");
            fixture=created.jsonPath().getString("id"); String root="/api/fixtures/"+fixture;
            String key="checkout-"+fixture;
            event(events,"FIXTURE","Created isolated sandbox fixture",Map.of("fixture",fixture,"seed",plan.seed()));
            try(Playwright pw=Playwright.create(); Browser browser=pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true)
                    .setArgs(List.of("--host-resolver-rules="+policy.chromiumResolverRules(),"--disable-dev-shm-usage")));
                BrowserContext context=browser.newContext(new Browser.NewContextOptions().setServiceWorkers(ServiceWorkerPolicy.BLOCK).setAcceptDownloads(false))) {
                context.setDefaultTimeout(5000); context.setDefaultNavigationTimeout(10000);
                context.route("**/*",route->{
                    URI uri=URI.create(route.request().url());
                    if(!policy.sameOrigin(uri)) route.abort(); else route.resume();
                });
                // Only local fixture data is captured. No real credentials are accepted in this release.
                context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(false));
                Page page=context.newPage();
                page.onConsoleMessage(m->{if(m.type().equals("error")) event(events,"CONSOLE",Redactor.text(m.text()).substring(0,Math.min(500,Redactor.text(m.text()).length())),Map.of());});
                page.onPageError(e->event(events,"JS_ERROR",Redactor.text(e),Map.of()));
                page.onResponse(r-> { String path=URI.create(r.url()).getPath(); event(events,"NETWORK",r.request().method()+" "+path,Map.of("status",r.status())); });
                page.navigate(target+"/?fixture="+fixture);
                page.getByTestId("add-to-cart").click();
                page.waitForFunction("JSON.parse(document.querySelector('#state').textContent).quantity === 1");
                parallel(plan.concurrency(),()->require(api.request("POST",root+"/coupon",Map.of("code","CHAOS10")).statusCode()==200,"Coupon request failed"));
                event(events,"ACTION","Applied overlapping coupon requests",Map.of("concurrency",plan.concurrency()));
                if(plan.category()==Category.BOUNDARY || plan.category()==Category.CONTRACT) {
                    int status=api.request("POST",root+"/cart",Map.of("quantity",-1)).statusCode();
                    checks.add(new Check("Invalid quantity rejected without server error","4xx",status,status>=400&&status<500,true));
                }
                if(plan.fault()!=Fault.NONE) installFault(page,root,plan,events);
                if(plan.category()==Category.CONCURRENCY) {
                    parallel(plan.concurrency(),()->api.request("POST",root+"/checkout",Map.of("idempotencyKey",key)));
                } else {
                    for(int i=0;i<plan.clicks();i++) {
                        checkpoint(cancelled,deadline);
                        page.getByTestId("checkout").click(new Locator.ClickOptions().setNoWaitAfter(true));
                    }
                }
                // Drain pending route callbacks before removing faults and inspecting final server state.
                page.waitForTimeout(Math.min(4000,plan.delayMs()+700));
                context.setOffline(false); page.unroute("**/checkout");
                require(api.request("POST",root+"/checkout",Map.of("idempotencyKey",key)).statusCode()<500 || defect.equals("TIMEOUT_INCONSISTENCY"),"Retry failed");
                event(events,"ACTION","Retried checkout with the original idempotency key",Map.of());
                if(plan.category()==Category.SESSION) {
                    api.request("POST",root+"/logout",Map.of());
                    int code=api.request("POST",root+"/checkout",Map.of("idempotencyKey",key)).statusCode();
                    checks.add(new Check("Protected checkout after logout",401,code,code==401,true));
                }
                if(plan.category()==Category.STATE) {
                    api.request("POST",root+"/cancel",Map.of()); api.request("POST",root+"/cancel",Map.of());
                }
                checkpoint(cancelled,deadline);
                var response=api.request("GET",root+"/state",null); require(response.statusCode()==200,"Independent state unavailable");
                JsonNode state=json.readTree(response.asString());
                check(checks,"No duplicate orders",1,state.path("orders").size());
                check(checks,"No duplicate mock captures",1,state.path("captures").asInt());
                check(checks,"Correct order total (cents)",9000,state.path("total").asInt());
                check(checks,"Inventory conserved",99,state.path("inventory").asInt());
                check(checks,"Coupon applied once",1,state.path("couponApplications").asInt());
                checks.add(new Check("Quantity remains nonnegative",">=0",state.path("quantity").asInt(),state.path("quantity").asInt()>=0,true));
                page.reload(); page.waitForFunction("document.querySelector('#state').textContent.includes('orders')");
                JsonNode ui=json.readTree(page.locator("#state").textContent());
                checks.add(new Check("UI and API state agree",state,ui,state.equals(ui),true));
                if(plan.category()==Category.STATE) checks.add(new Check("Repeated cancellation is stable","CANCELLED",state.path("orders").get(0).path("status").asText(),state.path("orders").get(0).path("status").asText().equals("CANCELLED"),true));
                page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve("final.png")).setFullPage(true)); artifacts.put("screenshot","final.png");
                Files.writeString(directory.resolve("state.json"),json.writeValueAsString(state)); artifacts.put("state","state.json");
                context.tracing().stop(new Tracing.StopOptions().setPath(directory.resolve("trace.zip")));
                artifacts.put("trace","trace.zip");
                long bytes=0;
                for(var entry:new ArrayList<>(artifacts.entrySet())) {
                    Path file=directory.resolve(entry.getValue());long size=Files.size(file);
                    if(bytes+size>50L*1024*1024){Files.delete(file);artifacts.remove(entry.getKey());event(events,"EVIDENCE_LIMIT","Artifact removed because the per-run 50 MiB evidence limit was reached",Map.of("artifact",entry.getKey()));}
                    else bytes+=size;
                }
                for(Check c:checks) if(!c.passed()) findings.add(new Finding(UUID.randomUUID(),c.invariant(),"HIGH","CONFIRMED",root+"/state",String.valueOf(c.expected()),String.valueOf(c.actual()),plan.steps(),Instant.now()));
                // Injected 5xx responses are expected fault evidence, never automatic defects.
                if(events.stream().anyMatch(e->e.kind().equals("JS_ERROR"))) findings.add(new Finding(UUID.randomUUID(),"Unhandled browser exception","MEDIUM","HIGH","/","No unhandled exception","See JS_ERROR timeline events",plan.steps(),Instant.now()));
                return new Result(findings.isEmpty()?Status.PASSED:Status.FAILED,events,checks,findings,artifacts);
            }
        } catch(CancellationException e) { event(events,"CANCELLED","Cancellation acknowledged",Map.of()); return new Result(Status.CANCELLED,events,checks,findings,artifacts); }
        catch(Exception e) {
            boolean timeout=System.nanoTime()>deadline;
            event(events,"ERROR",Redactor.text(e.getClass().getSimpleName()+": "+e.getMessage()),Map.of());
            return new Result(timeout?Status.TIMED_OUT:Status.INCONCLUSIVE,events,checks,findings,artifacts);
        } finally {
            if(fixture!=null) try { new SandboxClient(target,policy,()->false,System.nanoTime()+TimeUnit.SECONDS.toNanos(5)).request("DELETE","/api/fixtures/"+fixture,null); }
            catch(Exception e) { event(events,"CLEANUP_ERROR","Fixture cleanup failed; restart demo to clear abandoned fixtures",Map.of()); }
        }
    }
    private void installFault(Page page,String root,Plan p,List<Event> events) {
        page.route("**/checkout",route->{
            event(events,"FAULT",p.fault().name(),Map.of("path",root+"/checkout","delayMs",p.delayMs()));
            switch(p.fault()) {
                case BLOCK, TIMEOUT -> route.abort("timedout");
                case HTTP_500 -> route.fulfill(new Route.FulfillOptions().setStatus(500).setBody("injected"));
                case HTTP_503 -> route.fulfill(new Route.FulfillOptions().setStatus(503).setBody("injected"));
                case LOST_RESPONSE -> { route.fetch(); route.abort("failed"); }
                case DELAY -> { page.waitForTimeout(p.delayMs()); route.resume(); }
                case OFFLINE -> { page.context().setOffline(true); route.abort(); }
                default -> route.resume();
            }
        });
    }
    private static void checkpoint(BooleanSupplier c,long deadline) { if(c.getAsBoolean()||Thread.currentThread().isInterrupted()) throw new CancellationException(); if(System.nanoTime()>deadline) throw new IllegalStateException("Execution deadline exceeded"); }
    private static void parallel(int n,Runnable action) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(n);
        try { List<Callable<Void>> calls=new ArrayList<>(); for(int i=0;i<n;i++) calls.add(()->{action.run();return null;}); for(Future<Void> f:pool.invokeAll(calls,10,TimeUnit.SECONDS)) f.get(); }
        finally { pool.shutdownNow(); }
    }
    private static void check(List<Check> c,String name,int expected,int actual) { c.add(new Check(name,expected,actual,expected==actual,true)); }
    private static void require(boolean b,String message) { if(!b) throw new IllegalStateException(message); }
    private static void event(List<Event> e,String kind,String message,Map<String,Object> data) { if(e.size()<1000)e.add(new Event(Instant.now(),kind,message,data)); }
}
