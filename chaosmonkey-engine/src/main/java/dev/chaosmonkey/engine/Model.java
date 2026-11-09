package dev.chaosmonkey.engine;

import java.time.Instant;
import java.util.*;

public final class Model {
    private Model() {}
    public enum Category { RAPID, CONCURRENCY, NETWORK, BOUNDARY, SESSION, CONTRACT, STATE }
    public enum Fault { NONE, DELAY, BLOCK, HTTP_500, HTTP_503, TIMEOUT, LOST_RESPONSE, OFFLINE }
    public enum Status { QUEUED, RUNNING, PASSED, FAILED, INCONCLUSIVE, CANCELLED, TIMED_OUT }
    public record Plan(UUID id, String title, String description, Category category, long seed,
                       int clicks, int concurrency, int timeoutSeconds, Fault fault, int delayMs,
                       List<String> preconditions, Map<String,String> testData, List<String> steps,
                       List<String> invariants, String risk, String cleanup, Map<String,String> replay) {}
    public record Event(Instant at, String kind, String message, Map<String,Object> data) {}
    public record Check(String invariant, Object expected, Object actual, boolean passed, boolean verified) {}
    public record Finding(UUID id, String title, String severity, String confidence, String endpoint,
                          String expected, String actual, List<String> reproduction, Instant at) {}
    public record Result(Status status, List<Event> timeline, List<Check> assertions,
                         List<Finding> findings, Map<String,String> artifacts) {}
    public record Element(String id, String pageId, String role, String name, List<String> locators,
                          String workflow, List<String> interactions, double confidence) {}
    public record DiscoveredPage(String id, String url, String title, List<Element> elements) {}
    public record Endpoint(String method, String path, int status) {}
    public record ApplicationModel(List<DiscoveredPage> pages, List<Endpoint> endpoints, List<String> workflows) {}
}
