package dev.chaosmonkey.worker;

import dev.chaosmonkey.api.*;
import dev.chaosmonkey.api.Entities.*;
import dev.chaosmonkey.engine.*;
import dev.chaosmonkey.engine.Model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Component
public class JobRunner {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(JobRunner.class);
    private final Store db;private final Policy policy;private final ObjectMapper json;private final Path evidence;
    public JobRunner(Store db,Policy policy,ObjectMapper json,@Value("${chaos.evidence-directory}") String directory){this.db=db;this.policy=policy;this.json=json;this.evidence=Path.of(directory);}
    // One execution per worker process. Scale workers rather than creating unbounded threads.
    @org.springframework.amqp.rabbit.annotation.RabbitListener(queues="chaos.jobs",concurrency="1")
    public void notified(String executionId){tick();}
    @Scheduled(fixedDelay=1000) public synchronized void tick(){
        db.recover();
        for(Execution queued:db.queued(Execution.class)) {
            if(!db.claim(queued.id,"Execution"))continue;
            Execution e=db.get(Execution.class,queued.id);
            try {
                Scenario s=db.get(Scenario.class,e.scenarioId);Target t=db.get(Target.class,s.targetId);
                policy.target(t.baseUrl,t.authorized,t.environment);
                Result result=new CheckoutExecutor(policy).execute(t.baseUrl,json.readValue(s.plan,Plan.class),e.defect,evidence.resolve(e.id.toString()),()->db.get(Execution.class,e.id).cancelRequested);
                e.result=json.writeValueAsString(result);e.status=result.status().name();e.endedAt=Instant.now();List<FindingRow> rows=new ArrayList<>();
                for(Finding f:result.findings()){FindingRow row=new FindingRow();row.id=f.id();row.executionId=e.id;row.severity=f.severity();row.confidence=f.confidence();row.title=f.title();row.detail=json.writeValueAsString(f);rows.add(row);}
                db.finish(e,rows);
            }catch(Exception failure){log.error("Execution {} failed",e.id,failure);e.status="INCONCLUSIVE";e.endedAt=Instant.now();db.save(e);}
        }
        for(Discovery queued:db.queued(Discovery.class)) {
            if(!db.claim(queued.id,"Discovery"))continue;
            Discovery d=db.get(Discovery.class,queued.id);
            try {Target t=db.get(Target.class,d.targetId);policy.target(t.baseUrl,t.authorized,t.environment);d.model=json.writeValueAsString(new DiscoveryEngine(policy).discover(t.baseUrl,5,2));d.status="PASSED";}
            catch(Exception failure){d.status="INCONCLUSIVE";log.error("Discovery {} failed",d.id,failure);}
            db.save(d);
        }
    }
    @Scheduled(fixedDelay=3600000) public void retain() throws Exception {
        if(!Files.exists(evidence))return;
        try(var dirs=Files.list(evidence)) {
            for(Path dir:dirs.filter(Files::isDirectory).toList()) {
                if(Files.getLastModifiedTime(dir).toInstant().isBefore(Instant.now().minusSeconds(7*86400))) {
                    try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
                }
            }
        }
    }
}
