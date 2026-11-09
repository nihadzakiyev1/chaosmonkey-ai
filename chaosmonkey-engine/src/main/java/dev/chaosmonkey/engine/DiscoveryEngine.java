package dev.chaosmonkey.engine;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.util.*;
import static dev.chaosmonkey.engine.Model.*;

public final class DiscoveryEngine {
    private final Policy policy;
    public DiscoveryEngine(Policy policy) { this.policy=policy; }
    public ApplicationModel discover(String target,int maxPages,int maxDepth) {
        policy.target(target,true,"LOCAL_DEMO");
        if(maxPages<1||maxPages>10||maxDepth<0||maxDepth>3)throw new IllegalArgumentException("Discovery budget exceeded");
        SandboxClient api=new SandboxClient(target,policy,()->false,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(60));
        var fixtureResponse=api.request("POST","/api/fixtures",Map.of("defect","NONE"));
        if(fixtureResponse.statusCode()!=200)throw new IllegalStateException("Discovery fixture unavailable");
        String fixture=fixtureResponse.jsonPath().getString("id");
        List<DiscoveredPage> pages=new ArrayList<>(); List<Endpoint> endpoints=new ArrayList<>(); Set<String> seen=new HashSet<>(); Deque<Map.Entry<String,Integer>> queue=new ArrayDeque<>(); queue.add(Map.entry(target+"/?fixture="+fixture,0));
        try(Playwright pw=Playwright.create(); Browser browser=pw.chromium().launch(new BrowserType.LaunchOptions().setArgs(List.of("--host-resolver-rules="+policy.chromiumResolverRules())));
            BrowserContext context=browser.newContext(new Browser.NewContextOptions().setServiceWorkers(ServiceWorkerPolicy.BLOCK))) {
            final int[] requests={0};
            context.route("**/*",r->{if(++requests[0]>100||!policy.sameOrigin(URI.create(r.request().url())))r.abort();else r.resume();});
            Page page=context.newPage(); page.setDefaultTimeout(5000); page.setDefaultNavigationTimeout(10000);
            page.onResponse(r->{if(endpoints.size()<200) endpoints.add(new Endpoint(r.request().method(),URI.create(r.url()).getPath(),r.status()));});
            while(!queue.isEmpty()&&pages.size()<maxPages) {
                var next=queue.remove(); if(!seen.add(next.getKey()))continue;
                page.navigate(next.getKey()); page.waitForTimeout(300);
                String id=UUID.nameUUIDFromBytes(next.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(); List<Element> elements=new ArrayList<>();
                Locator interactive=page.locator("a,button,input,select,textarea,form,[role=dialog],nav");
                for(int i=0;i<Math.min(interactive.count(),100);i++) {
                    Locator l=interactive.nth(i); String tag=(String)l.evaluate("e=>e.tagName.toLowerCase()"); String role=l.getAttribute("role");
                    String type=Objects.toString(l.getAttribute("type"),"");
                    if(role==null)role=switch(tag){case "a"->"link";case "select"->"combobox";case "textarea"->"textbox";case "nav"->"navigation";case "input"->Set.of("checkbox","radio").contains(type)?type:"textbox";default->tag;};
                    String name=(String)l.evaluate("e=>e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].textContent) || e.getAttribute('placeholder') || e.textContent || ''");name=name.strip();if(name.length()>100)name=name.substring(0,100);
                    List<String> locators=new ArrayList<>(); String test=l.getAttribute("data-testid"); String elementId=l.getAttribute("id");
                    if(test!=null)locators.add("testId="+test); if(elementId!=null)locators.add("id="+elementId); locators.add("role="+role+" name="+name);
                    List<String> interactions=type.equals("file")?List.of("upload"):role.equals("checkbox")?List.of("check","uncheck"):tag.equals("select")?List.of("select"):List.of(Set.of("input","textarea").contains(tag)?"fill":"click");
                    elements.add(new Element(id+"-"+i,id,role,name,locators,"checkout",interactions,test==null?.75:.98));
                    if(tag.equals("a")&&next.getValue()<maxDepth) { String href=l.getAttribute("href"); if(href!=null&&!href.startsWith("#")) { URI u=URI.create(next.getKey()).resolve(href); if(policy.sameOrigin(u)&&u.getQuery()==null)queue.add(Map.entry(u.toString(),next.getValue()+1)); } }
                }
                pages.add(new DiscoveredPage(id,page.url(),page.title(),elements));
            }
        } finally {
            new SandboxClient(target,policy,()->false,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)).request("DELETE","/api/fixtures/"+fixture,null);
        }
        return new ApplicationModel(pages,endpoints.stream().distinct().toList(),List.of("catalog → cart → coupon → checkout → order history"));
    }
    public List<Endpoint> importOpenApi(String specification) throws Exception {
        if(specification.length()>1_000_000)throw new IllegalArgumentException("Specification too large");
        JsonNode root=new ObjectMapper().readTree(specification); List<Endpoint> result=new ArrayList<>();
        if(!root.has("openapi")||!root.path("paths").isObject())throw new IllegalArgumentException("OpenAPI JSON with paths required");
        root.path("paths").fields().forEachRemaining(p->p.getValue().fieldNames().forEachRemaining(m->{if(Set.of("get","post","put","patch","delete","head","options").contains(m))result.add(new Endpoint(m.toUpperCase(),p.getKey(),0));}));
        return result; // Imported endpoints are descriptive; no imported URL is executed.
    }
}
