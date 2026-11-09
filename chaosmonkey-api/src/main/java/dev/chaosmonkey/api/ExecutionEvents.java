package dev.chaosmonkey.api;

import dev.chaosmonkey.api.Entities.Execution;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Component
public class ExecutionEvents {
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger clients=new AtomicInteger();
    private final Store db;
    public ExecutionEvents(Store db){this.db=db;}
    public SseEmitter subscribe(UUID id){
        if(clients.incrementAndGet()>20){clients.decrementAndGet();throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);}
        SseEmitter emitter=new SseEmitter(130_000L);AtomicBoolean closed=new AtomicBoolean();AtomicReference<ScheduledFuture<?>> task=new AtomicReference<>();
        Runnable cleanup=()->{if(closed.compareAndSet(false,true)){clients.decrementAndGet();var future=task.get();if(future!=null)future.cancel(false);}};
        emitter.onCompletion(cleanup);emitter.onTimeout(cleanup);emitter.onError(e->cleanup.run());
        task.set(timer.scheduleAtFixedRate(()->{if(closed.get())return;try{Execution e=db.get(Execution.class,id);emitter.send(SseEmitter.event().name("execution").id(e.id+":"+e.status).data(e));if(!e.status.equals("QUEUED")&&!e.status.equals("RUNNING")){emitter.complete();cleanup.run();}}catch(Exception failure){emitter.completeWithError(failure);cleanup.run();}},100,1000,TimeUnit.MILLISECONDS));
        return emitter;
    }
    @PreDestroy public void close(){timer.shutdownNow();}
}
