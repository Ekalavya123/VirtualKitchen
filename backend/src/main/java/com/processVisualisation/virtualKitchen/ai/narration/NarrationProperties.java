package com.processVisualisation.virtualKitchen.ai.narration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code ai.narration.*}: provider-independent narration settings. Which provider speaks is
 * not configured here — that is the model registry's {@code ai.default-model.TEXT_TO_SPEECH}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.narration")
public class NarrationProperties {

    /** BCP-47 language passed to providers that accept one. */
    private String languageCode = "en-US";
    /** Voice override for every provider; blank means each provider's own configured default. */
    private String voice;
    /** Scripts longer than this are cut at a word boundary, to cap per-step cost. */
    private int maxScriptChars = 1200;
    /**
     * How long a claimed generation is trusted before another request may take it over. Must exceed
     * the slowest provider call including the queue's retries, or a slow call gets duplicated.
     */
    private long leaseMs = 300_000;
    /** After a failure, how long the same text is not retried automatically (force still can). */
    private long failedRetryAfterMs = 30_000;
    private Storage storage = new Storage();

    @Data
    public static class Storage {
        /** {@code filesystem} (development default) or {@code supabase}. */
        private String type = "filesystem";
        private FileSystem filesystem = new FileSystem();
        private Supabase supabase = new Supabase();
    }

    @Data
    public static class FileSystem {
        private String root = "./data/narrations";
        /** Origin the browser reaches the backend on; audio URLs are built from it. */
        private String publicBaseUrl = "http://localhost:8080";
    }

    @Data
    public static class Supabase {
        private String pathPrefix = "narrations";
    }
}
