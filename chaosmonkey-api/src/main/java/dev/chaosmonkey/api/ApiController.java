package dev.chaosmonkey.api;

import com.fasterxml.jackson.databind.*;
import dev.chaosmonkey.ai.ScenarioGenerator;
import dev.chaosmonkey.engine.*;
import dev.chaosmonkey.engine.Model.*;
import dev.chaosmonkey.api.Entities.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.security.Principal;
import java.util.*;

@RestController @RequestMapping("/api/v1")
public class ApiController {
    private final Store db; private final Policy policy; private final ObjectMapper json; private final Path evidence;
    private final ExecutionEvents streams;
    private final JobNotifications notifications;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbit;
    @Value("${chaos.ai.base-url:}") private String aiBase;
    @Value("${chaos.ai.api-key:}") private String aiKey;
    @Value("${chaos.ai.model:}") private String aiModel;
    public ApiController(Store db,Policy policy,ObjectMapper json,ExecutionEvents streams,JobNotifications notifications,org.springframework.amqp.rabbit.core.RabbitTemplate rabbit,@Value("${chaos.evidence-directory}") String evidence) { this.db=db;this.policy=policy;this.json=json;this.streams=streams;this.notifications=notifications;this.rabbit=rabbit;this.evidence=Path.of(evidence).toAbsolutePath().normalize(); }
    public record ProjectInput(@NotBlank @Size(max=120) String name) {}
    public record TargetInput(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=500) String baseUrl,@NotBlank String environment,boolean authorized) {}
    public record GenerateInput(@NotNull UUID targetId,@NotNull Category category,long seed,@Min(1) @Max(30) int clicks,@Min(1) @Max(4) int concurrency,@NotNull Fault fault,@Min(0) @Max(3000) int delayMs,boolean ai) {}
    public record RunInput(@NotNull UUID scenarioId,@Pattern(regexp="NONE|DUPLICATE_ORDER|DOUBLE_COUPON|INCORRECT_TOTAL|INVENTORY_RACE|MISSING_IDEMPOTENCY|TIMEOUT_INCONSISTENCY") @NotNull String defect,boolean confirmed) {}
    public record TargetId(@NotNull UUID targetId) {}
    public record ExecutionId(@NotNull UUID executionId) {}
    private Project project(UUID id,Principal user) { Project p=db.get(Project.class,id); if(!p.owner.equals(user.getName()))throw new ResponseStatusException(HttpStatus.FORBIDDEN);return p; }
    private Target target(UUID id,Principal user) { Target t=db.get(Target.class,id);project(t.projectId,user);return t; }
    private Scenario scenario(UUID id,Principal user) { Scenario s=db.get(Scenario.class,id);target(s.targetId,user);return s; }
    private Execution execution(UUID id,Principal user) { Execution e=db.get(Execution.class,id);scenario(e.scenarioId,user);return e; }
    private void audit(Principal u,String action,UUID id) { Audit a=new Audit();a.actor=u.getName();a.action=action;a.resourceId=id;db.save(a); }
    @PostMapping("/auth/login") public Map<String,String> login(Principal u) { return Map.of("username",u.getName()); }
    @GetMapping("/settings") public Map<String,Object> settings(@Value("${chaos.demo-url}") String demo) { return Map.of("demoUrl",demo,"externalTargets",false,"maxConcurrency",4,"maxClicks",30,"aiEnabled",!aiBase.isBlank()&&!aiKey.isBlank()&&!aiModel.isBlank()); }
    @PostMapping("/projects") public Project create(@Valid @RequestBody ProjectInput in,Principal u) { Project p=new Project();p.name=in.name();p.owner=u.getName();p=db.save(p);audit(u,"PROJECT_CREATED",p.id);return p; }
    @GetMapping("/projects") public List<Project> projects(Principal u) { return db.projects(u.getName()); }
    @PostMapping("/projects/{id}/targets") public Target addTarget(@PathVariable UUID id,@Valid @RequestBody TargetInput in,Principal u) { project(id,u);policy.target(in.baseUrl(),in.authorized(),in.environment());Target t=new Target();t.projectId=id;t.name=in.name();t.baseUrl=in.baseUrl().replaceAll("/$","");t.environment=in.environment();t.authorized=in.authorized();t=db.save(t);audit(u,"TARGET_AUTHORIZED",t.id);return t; }
    @GetMapping("/projects/{id}/targets") public List<Target> targets(@PathVariable UUID id,Principal u) {project(id,u);return db.targets(id);}
    @PostMapping("/scenarios/generate") public Scenario generate(@Valid @RequestBody GenerateInput in,Principal u) throws Exception { Target t=target(in.targetId(),u);policy.target(t.baseUrl,t.authorized,t.environment);var generator=new ScenarioGenerator();Plan p=generator.generate(in.category(),in.seed(),in.clicks(),in.concurrency(),in.fault(),in.delayMs());if(in.ai())p=generator.suggest(p,policy,aiBase,aiKey,aiModel);policy.validate(p);Scenario s=new Scenario();s.targetId=t.id;s.plan=json.writeValueAsString(p);return db.save(s); }
    @GetMapping("/scenarios") public List<Scenario> scenarios(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="50") @Min(1) @Max(200) int size,Principal u) { return db.list(Scenario.class,page,size).stream().filter(s->ownsTarget(s.targetId,u)).toList(); }
    private boolean ownsTarget(UUID id,Principal u) { return db.get(Project.class,db.get(Target.class,id).projectId).owner.equals(u.getName()); }
    private boolean ownsExecution(UUID id,Principal u) { return ownsTarget(db.get(Scenario.class,db.get(Execution.class,id).scenarioId).targetId,u); }
    @PostMapping("/executions") public Execution run(@Valid @RequestBody RunInput in,Principal u) throws Exception {
        Scenario s=scenario(in.scenarioId(),u);Target t=target(s.targetId,u);policy.target(t.baseUrl,t.authorized,t.environment);policy.validate(json.readValue(s.plan,Plan.class));
        if(!in.confirmed())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirm state-changing sandbox execution");
        if(db.queued(Execution.class).size()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Local queue full");
        Execution e=new Execution();e.scenarioId=s.id;e.defect=in.defect();e=db.save(e);audit(u,"EXECUTION_CONFIRMED",e.id);notifications.publish(rabbit,e.id);return e;
    }
    @GetMapping("/executions") public List<Execution> executions(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="50") @Min(1) @Max(200) int size,Principal u) {return db.list(Execution.class,page,size).stream().filter(e->ownsExecution(e.id,u)).toList();}
    @GetMapping("/executions/{id}") public Execution getExecution(@PathVariable UUID id,Principal u) {return execution(id,u);}
    @PostMapping("/executions/{id}/cancel") public Map<String,String> cancel(@PathVariable UUID id,Principal u) {execution(id,u);db.cancel(id);audit(u,"CANCEL_REQUESTED",id);return Map.of("status","CANCELLATION_REQUESTED");}
    @GetMapping(value="/executions/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public org.springframework.web.servlet.mvc.method.annotation.SseEmitter events(@PathVariable UUID id,Principal u) {execution(id,u);return streams.subscribe(id);}
    @PostMapping("/discovery-sessions") public Discovery discover(@Valid @RequestBody TargetId in,Principal u) {target(in.targetId(),u);if(db.queued(Discovery.class).size()>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);Discovery d=new Discovery();d.targetId=in.targetId();return db.save(d);}
    @GetMapping("/discovery-sessions/{id}") public Discovery discovery(@PathVariable UUID id,Principal u) {Discovery d=db.get(Discovery.class,id);target(d.targetId,u);return d;}
    @PostMapping("/openapi/import") public List<Endpoint> importSpec(@RequestBody @Size(max=1000000) String spec) throws Exception {return new DiscoveryEngine(policy).importOpenApi(spec);}
    @GetMapping("/findings") public List<FindingRow> findings(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="100") @Min(1) @Max(200) int size,Principal u) {return db.list(FindingRow.class,page,size).stream().filter(f->ownsExecution(f.executionId,u)).toList();}
    @GetMapping("/findings/{id}") public FindingRow finding(@PathVariable UUID id,Principal u) {FindingRow f=db.get(FindingRow.class,id);execution(f.executionId,u);return f;}
    @PostMapping("/reports") public Report report(@Valid @RequestBody ExecutionId in,Principal u) throws Exception {
        Execution e=execution(in.executionId(),u);if(e.result==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Execution has no evidence yet");
        Scenario s=scenario(e.scenarioId,u);Target t=target(s.targetId,u);
        Report r=new Report();r.executionId=e.id;r.document=json.writeValueAsString(Map.of("product","ChaosMonkey QA","execution",e,"target",Map.of("name",t.name,"baseUrl",t.baseUrl,"environment",t.environment),"scenario",json.readTree(s.plan),"recommendedAction","Reproduce confirmed invariant failures against the same fixture version; inspect attached state and trace evidence."));return db.save(r);
    }
    @GetMapping("/reports") public List<Report> reports(Principal u) {return db.list(Report.class,0,100).stream().filter(r->ownsExecution(r.executionId,u)).toList();}
    @GetMapping("/reports/{id}") public ResponseEntity<String> getReport(@PathVariable UUID id,@RequestParam(defaultValue="json") String format,Principal u) throws Exception {Report r=db.get(Report.class,id);execution(r.executionId,u);if(format.equals("html"))return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(new ReportRenderer(json).html(r.document,evidence.resolve(r.executionId.toString()).resolve("final.png")));return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(r.document);}
    @GetMapping("/executions/{id}/evidence/{file}") public ResponseEntity<byte[]> evidence(@PathVariable UUID id,@PathVariable String file,Principal u) throws Exception {
        execution(id,u);if(!Set.of("final.png","trace.zip","state.json","scenario.json").contains(file))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Path path=evidence.resolve(id.toString()).resolve(file).normalize();if(!path.startsWith(evidence)||!Files.exists(path))throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        MediaType type=file.endsWith("png")?MediaType.IMAGE_PNG:file.endsWith("json")?MediaType.APPLICATION_JSON:MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().contentType(type).header("Content-Disposition","attachment; filename=\""+file+"\"").body(Files.readAllBytes(path));
    }
    private String escape(String s) {return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
}
