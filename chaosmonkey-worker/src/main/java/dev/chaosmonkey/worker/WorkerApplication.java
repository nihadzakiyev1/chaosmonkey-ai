package dev.chaosmonkey.worker;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication(scanBasePackages={"dev.chaosmonkey.api","dev.chaosmonkey.worker"})
@EntityScan("dev.chaosmonkey.api") @EnableScheduling
public class WorkerApplication {public static void main(String[] args){SpringApplication.run(WorkerApplication.class,args);}}
