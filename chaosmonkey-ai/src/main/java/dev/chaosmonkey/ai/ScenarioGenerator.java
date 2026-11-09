package dev.chaosmonkey.ai;

import dev.chaosmonkey.engine.Model.*;
import dev.chaosmonkey.engine.Policy;
import dev.langchain4j.model.openai.OpenAiChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class ScenarioGenerator {
    public Plan generate(Category category, long seed, int clicks, int concurrency, Fault fault, int delay) {
        UUID id = UUID.nameUUIDFromBytes((category+":"+seed+":"+clicks+":"+concurrency+":"+fault+":"+delay).getBytes(StandardCharsets.UTF_8));
        return new Plan(id, "Chaos Checkout · " + category, "Explore the sandbox checkout using a reproducible fixture and bounded actions.",
            category, seed, clicks, concurrency, 60, fault, delay,
            List.of("Authorized isolated demo", "Fresh per-execution cart", "Mock payments only"), Map.of("coupon", "CHAOS10", "quantity", "1"),
            List.of("Create isolated fixture", "Add one product", "Apply coupon concurrently", "Submit using one idempotency key", "Retry after interruption", "Refresh and independently inspect state"),
            List.of("one order", "one mock capture", "total 9000 cents", "inventory 99", "nonnegative quantity", "one coupon application", "UI/API consistency"),
            "HIGH", "Delete this execution's isolated fixture", Map.of("generator", "rules-v1", "fixture", "catalog-v1", "seed", Long.toString(seed)));
    }
    /** One optional suggestion call. No model-supplied URLs, commands or steps are executed. */
    public Plan suggest(Plan fallback, Policy policy, String baseUrl, String key, String model) {
        if (baseUrl == null || baseUrl.isBlank() || key == null || key.isBlank()) return fallback;
        try {
            var llm = OpenAiChatModel.builder().baseUrl(baseUrl).apiKey(key).modelName(model)
                .temperature(0.0).maxTokens(256).maxRetries(0).timeout(Duration.ofSeconds(15)).build();
            String answer = llm.chat("Choose one checkout risk. Return only JSON: {\"category\":\"RAPID|CONCURRENCY|NETWORK|BOUNDARY|SESSION|CONTRACT|STATE\",\"clicks\":15,\"concurrency\":2}. Limits clicks 1..30, concurrency 1..4. No other keys.");
            var node = new ObjectMapper().readTree(answer);
            if (!node.isObject() || node.size() != 3 || !node.path("clicks").isIntegralNumber() || !node.path("concurrency").isIntegralNumber()) return fallback;
            Plan p = generate(Category.valueOf(node.path("category").asText()), fallback.seed(), node.path("clicks").intValue(), node.path("concurrency").intValue(), fallback.fault(), fallback.delayMs());
            policy.validate(p); return source(p,"langchain4j-v1",model);
        } catch (Exception e) { return source(fallback,"rules-v1","provider-fallback:"+e.getClass().getSimpleName()); }
    }
    private Plan source(Plan p,String generator,String detail) {
        Map<String,String> metadata=new HashMap<>(p.replay());metadata.put("generator",generator);metadata.put("providerOutcome",detail);
        return new Plan(p.id(),p.title(),p.description(),p.category(),p.seed(),p.clicks(),p.concurrency(),p.timeoutSeconds(),p.fault(),p.delayMs(),p.preconditions(),p.testData(),p.steps(),p.invariants(),p.risk(),p.cleanup(),Map.copyOf(metadata));
    }
}
