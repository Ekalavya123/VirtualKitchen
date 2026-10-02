package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps one Gemini explicit context cache ({@code cachedContents}) per (model, system prompt) pair, so a static
 * system prompt is stored once on Gemini's side and referenced by name instead of being re-sent on every request.
 * <p>
 * Caches are tracked in memory per instance and created lazily: the first request after start-up (or after a
 * cache expired) creates one, concurrent requests for the same key wait for that single creation. A cache is bound
 * to the model it was created for, so the model is part of the key. Creation never fails a request — on any
 * error the caller simply sends the system prompt inline, and creation is not retried for a back-off period
 * (e.g. a prompt below the model's minimum cacheable size would otherwise be retried on every call).
 */
@Component
public class GeminiPromptCacheService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiPromptCacheService.class);

    /** A created cache ({@code name} set) or a remembered creation failure ({@code name} null) — both expire. */
    private record Entry(String name, Instant usableUntil) {}

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Object> creationLocks = new ConcurrentHashMap<>();

    @Autowired
    public GeminiPromptCacheService(@Qualifier("geminiRestClient") RestClient restClient, GeminiProperties properties) {
        this(restClient, properties, Clock.systemUTC());
    }

    GeminiPromptCacheService(RestClient restClient, GeminiProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * The name ({@code cachedContents/...}) of a live cache holding {@code systemPrompt} for {@code model},
     * creating one if needed; empty when caching is disabled or unavailable, in which case the caller sends the
     * system prompt inline.
     */
    public Optional<String> cacheNameFor(String model, String systemPrompt) {
        if (!properties.getCache().isEnabled() || !StringUtils.hasText(model) || !StringUtils.hasText(systemPrompt)) {
            return Optional.empty();
        }
        String key = key(model, systemPrompt);
        Entry entry = usable(entries.get(key));
        if (entry == null) {
            synchronized (creationLocks.computeIfAbsent(key, k -> new Object())) {
                entry = usable(entries.get(key));
                if (entry == null) {
                    entry = create(model, systemPrompt);
                    entries.put(key, entry);
                }
            }
        }
        return Optional.ofNullable(entry.name());
    }

    /** Forgets the cache for this pair (e.g. Gemini reported it missing), so the next request creates a new one. */
    public void evict(String model, String systemPrompt) {
        entries.remove(key(model, systemPrompt));
    }

    private Entry usable(Entry entry) {
        return entry != null && clock.instant().isBefore(entry.usableUntil()) ? entry : null;
    }

    private Entry create(String model, String systemPrompt) {
        GeminiProperties.Cache config = properties.getCache();
        long startedAt = System.nanoTime();
        try {
            Map<String, Object> body = Map.of(
                    "model", "models/" + model,
                    "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                    "ttl", config.getTtlSeconds() + "s"
            );
            String rawResponse = restClient.post()
                    .uri(config.getEndpoint())
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(rawResponse);
            String name = root.path("name").asText(null);
            if (!StringUtils.hasText(name)) {
                throw new IllegalStateException("cachedContents response has no name");
            }
            Instant expireTime = root.hasNonNull("expireTime")
                    ? Instant.parse(root.get("expireTime").asText())
                    : clock.instant().plusSeconds(config.getTtlSeconds());
            logger.info("Created Gemini prompt cache {} for model {} ({} tokens, expires {}, {} ms)", name, model,
                    root.path("usageMetadata").path("totalTokenCount").asText("NA"), expireTime, elapsedMs(startedAt));
            return new Entry(name, expireTime.minusSeconds(config.getRefreshMarginSeconds()));
        } catch (Exception ex) {
            logger.warn("Gemini prompt cache creation failed for model {}; sending the system prompt inline for the next {}s: {}",
                    model, config.getFailureBackoffSeconds(), ex.getMessage());
            return new Entry(null, clock.instant().plusSeconds(config.getFailureBackoffSeconds()));
        }
    }

    private static String key(String model, String systemPrompt) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(systemPrompt.getBytes(StandardCharsets.UTF_8));
            return model + ":" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
