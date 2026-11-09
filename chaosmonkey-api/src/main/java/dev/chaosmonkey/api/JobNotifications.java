package dev.chaosmonkey.api;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.*;
import java.util.UUID;
@Configuration
public class JobNotifications {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(JobNotifications.class);
    @org.springframework.beans.factory.annotation.Value("${chaos.broker-notifications:true}") private boolean enabled;
    @Bean Queue jobQueue(){return new Queue("chaos.jobs",true);}
    public void publish(RabbitTemplate rabbit,UUID id){if(!enabled)return;try{rabbit.convertAndSend("chaos.jobs",id.toString());}catch(org.springframework.amqp.AmqpException e){log.warn("Job {} persisted; broker unavailable, database polling will recover it",id);}}
}
