package com.processVisualisation.virtualKitchen.ai.analytics.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Analytics record of one <em>logical</em> AI request: what it was for, which model served it, every provider
 * call (attempt) it made, what those calls used and cost, and how it ended.
 * <p>
 * Source of truth split: {@code ai_request_jobs} stays authoritative for queueing and credits; this collection is
 * authoritative for usage, cost and timing analytics. When a job exists the two share an id. Raw prompts and
 * generated outputs are not copied here: {@link OutputRef} points at where the existing stores keep them.
 * <p>
 * Every usage, cost and timing field is nullable, and null means "not reported / not recorded" — never zero.
 * Writes go through {@code AiAnalyticsRecorder}, which updates a record with a compare-and-set on
 * {@link #revision}, so concurrent attempt/completion writes never overwrite one another.
 */
@Data
@Document(collection = "ai_request_analytics")
@CompoundIndexes({
        @CompoundIndex(name = "context_created_idx", def = "{'operationContext': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "model_created_idx", def = "{'resolvedModelKey': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_created_idx", def = "{'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "type_created_idx", def = "{'requestType': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "status_updated_idx", def = "{'status': 1, 'updatedAt': 1}")
})
public class AiRequestAnalytics {

    @Id
    private String id;

    /** Compare-and-set counter; incremented by every write. */
    private long revision;

    private AnalyticsSource source;
    private String aiRequestJobId;
    /** For a {@link AnalyticsStatus#REUSED} request: the request that originally produced the reused output. */
    private String reusedFromRequestId;

    // --- correlation ---
    @Indexed
    private Long userId;
    @Indexed
    private String jobId;
    @Indexed
    private String workflowId;
    @Indexed
    private String recipeId;
    private String processId;
    @Indexed
    private String stepId;
    private String correlationType;
    private String correlationId;
    private String idempotencyKey;
    private String artifactId;
    private OutputRef outputRef;

    // --- classification ---
    private AnalyticsRequestType requestType;
    private OperationContext operationContext;

    // --- model ---
    private String requestedModelKey;
    private String resolvedModelKey;
    private String finalProvider;
    private String finalProviderModelId;
    private String modelTier;
    private Boolean usedFallback;
    private String fallbackReason;
    private Map<String, Object> modelSnapshot;
    private Integer creditCost;
    private String creditTransactionId;

    // --- status ---
    private AnalyticsStatus status;
    private String errorCategory;
    private String errorMessage;
    private Boolean retryable;
    private Boolean validationPassed;
    private List<StatusChange> statusHistory = new ArrayList<>();

    // --- timing (all nullable) ---
    @Indexed(direction = org.springframework.data.mongodb.core.index.IndexDirection.DESCENDING)
    private Instant createdAt;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;
    private Long queueWaitMs;
    private Long executionMs;
    @Indexed
    private Long totalMs;
    private Long timeoutMs;
    private Integer queueDepthAtSubmit;
    private String poolName;

    // --- attempts and roll-ups ---
    /** Transport-level attempts made by the queue (1 + retries); null when not recorded. */
    private Integer transportAttempts;
    private List<Attempt> attempts = new ArrayList<>();
    /** Provider calls made; for historical records this comes from the legacy call counter. */
    private Integer attemptCount;
    private Integer failedAttemptCount;
    private Usage usageTotals;
    @Indexed
    private Double totalCost;
    private Cost costTotals;

    private DataCompleteness dataCompleteness;
    private List<String> missingFields = new ArrayList<>();
    private String promptHash;
    private Integer promptChars;

    /** One provider call. */
    @Data
    public static class Attempt {
        private String attemptId;
        private int callIndex;
        private Integer transportAttempt;
        private String purpose;
        private String provider;
        private String providerModelId;
        private String reportedModel;
        private Instant startedAt;
        private Instant endedAt;
        private Long durationMs;
        private AnalyticsStatus status;
        private String errorCategory;
        private String errorMessage;
        private Integer httpStatus;
        private String finishReason;
        private Boolean validationPassed;
        private List<String> validationErrors;
        /** True when the call finished after its request had already reached a terminal state (e.g. timed out). */
        private boolean late;
        private Usage usage;
        private Cost cost;
        /** {@code ai_responses} id holding this call's text output, when retained. */
        private String outputId;
    }

    /** Usage of one attempt, or summed over a request's attempts. Null fields were not reported. */
    @Data
    public static class Usage {
        private Long inputTokens;
        private Long cachedInputTokens;
        private Long outputTokens;
        private Long reasoningTokens;
        private Long totalTokens;
        private Integer imageCount;
        private Integer imageWidth;
        private Integer imageHeight;
        private Long audioDurationMs;
        private Integer inputChars;
        private Integer outputChars;
        private Long outputBytes;
        private Long providerDurationMs;
        private UsageBasis usageBasis;
    }

    /** Cost of one attempt, or summed over a request's attempts. */
    @Data
    public static class Cost {
        private Double inputCost;
        private Double cachedInputCost;
        private Double outputCost;
        private Double reasoningCost;
        private Double modalityCost;
        /** What the provider bills (0 for a local engine). */
        private Double directCost;
        /** Estimated compute/infrastructure cost (local engines); null when no rate is configured. */
        private Double infraCost;
        /** directCost + infraCost (when known). */
        private Double totalCost;
        private String currency;
        private CostBasis costBasis;
        private CostBasis infraCostBasis;
        private String pricingRateId;
        private String pricingVersion;
        private Instant calculatedAt;
        private String formula;
        /** Request totals only: false when at least one billable attempt could not be costed. */
        private Boolean complete;
        private Integer costedAttempts;
        private Integer uncostedAttempts;
    }

    /** Where a request's result lives in the existing stores. */
    @Data
    public static class OutputRef {
        /** TEXT, JSON, IMAGE or AUDIO. */
        private String kind;
        /** Collection holding the result, e.g. {@code ai_responses}, {@code process_generation_job}. */
        private String collection;
        private String id;
        /** Exact storage URL the request produced (images, audio); immune to later regeneration of the asset. */
        private String url;
        private String mimeType;
        private Long sizeBytes;
        private Instant storedAt;
    }

    @Data
    public static class StatusChange {
        private AnalyticsStatus status;
        private Instant at;

        public static StatusChange of(AnalyticsStatus status, Instant at) {
            StatusChange change = new StatusChange();
            change.setStatus(status);
            change.setAt(at);
            return change;
        }
    }
}
