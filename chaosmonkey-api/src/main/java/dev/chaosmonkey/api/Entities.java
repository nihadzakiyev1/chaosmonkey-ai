package dev.chaosmonkey.api;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

public final class Entities {
    @MappedSuperclass public abstract static class Row {
        @Id public UUID id=UUID.randomUUID();
        @Column(nullable=false) public Instant createdAt=Instant.now();
    }
    @Entity(name="Project") @Table(name="projects") public static class Project extends Row {
        @Column(nullable=false) public String name;
        @Column(nullable=false) public String owner;
    }
    @Entity(name="Target") @Table(name="targets") public static class Target extends Row {
        @Column(nullable=false) public UUID projectId;
        @Column(nullable=false) public String name;
        @Column(nullable=false) public String baseUrl;
        @Column(nullable=false) public String environment="LOCAL_DEMO";
        @Column(nullable=false) public boolean authorized;
    }
    @Entity(name="Scenario") @Table(name="scenarios") public static class Scenario extends Row {
        @Column(nullable=false) public UUID targetId;
        @Column(nullable=false,columnDefinition="text") public String plan;
    }
    @Entity(name="Execution") @Table(name="executions") public static class Execution extends Row {
        @Column(nullable=false) public UUID scenarioId;
        @Column(nullable=false) public String status="QUEUED";
        @Column(nullable=false) public String defect="NONE";
        @Column(nullable=false) public boolean cancelRequested;
        public Instant startedAt;
        public Instant endedAt;
        @Column(columnDefinition="text") public String result;
    }
    @Entity(name="Discovery") @Table(name="discoveries") public static class Discovery extends Row {
        @Column(nullable=false) public UUID targetId;
        @Column(nullable=false) public String status="QUEUED";
        @Column(columnDefinition="text") public String model;
    }
    @Entity(name="FindingRow") @Table(name="findings") public static class FindingRow extends Row {
        @Column(nullable=false) public UUID executionId;
        @Column(nullable=false) public String severity;
        @Column(nullable=false) public String confidence;
        @Column(nullable=false) public String title;
        @Column(nullable=false,columnDefinition="text") public String detail;
    }
    @Entity(name="Report") @Table(name="reports") public static class Report extends Row {
        @Column(nullable=false) public UUID executionId;
        @Column(nullable=false,columnDefinition="text") public String document;
    }
    @Entity(name="Audit") @Table(name="audit_logs") public static class Audit extends Row {
        @Column(nullable=false) public String actor;
        @Column(nullable=false) public String action;
        @Column(nullable=false) public UUID resourceId;
    }
    private Entities() {}
}

