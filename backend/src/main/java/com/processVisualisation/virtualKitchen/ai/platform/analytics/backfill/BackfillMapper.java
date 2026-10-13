package com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsErrors;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Cost;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.OutputRef;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Usage;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsRequestType;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsSource;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.DataCompleteness;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.UsageBasis;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Turns existing records into historical analytics records, recovering only what those records actually hold.
 * Pure: no I/O, so every recovery rule is unit-testable. The rules:
 * <ul>
 *     <li>Values that were recorded are copied; values that can be derived exactly (a queue wait from two recorded
 *     timestamps) are derived; everything else stays null and is listed in {@code missingFields}.</li>
 *     <li>Token usage: the old usage accumulator stored a missing count as 0, so an all-zero usage block (local
 *     engines, or a call that failed before reporting) is recorded as {@link UsageBasis#NOT_REPORTED}, not zero.</li>
 *     <li>Cost: the stored legacy estimate is kept, labelled {@link CostBasis#ESTIMATED} because the rates it used were
 *     never recorded; a paid request without one is {@link CostBasis#UNAVAILABLE}, never zero. A local engine's
 *     direct cost is a calculated 0 (it has no provider bill).</li>
 *     <li>No per-attempt detail exists historically, so {@code attempts} stays empty.</li>
 * </ul>
 */
public final class BackfillMapper {

    public static final String LEGACY_PRICING_VERSION = "legacy-config (rate version not recorded)";

    private BackfillMapper() {
    }

    /** A historical record for one {@code ai_request_jobs} document; correlation fields are filled in by the caller. */
    public static AiRequestAnalytics fromRequestJob(Document job, Instant now) {
        AiRequestAnalytics record = new AiRequestAnalytics();
        List<String> missing = new ArrayList<>();
        String id = string(job.get("_id"));
        record.setId(id);
        record.setAiRequestJobId(id);
        record.setSource(AnalyticsSource.BACKFILL_REQUEST_JOB);
        record.setUserId(longValue(job.get("userId")));
        record.setRequestType(requestType(job.getString("capability")));
        record.setCorrelationType(job.getString("correlationType"));
        record.setCorrelationId(job.getString("correlationId"));
        record.setOperationContext(OperationContext.fromCorrelationType(job.getString("correlationType")));
        record.setIdempotencyKey(job.getString("idempotencyKey"));
        record.setJobId(job.getString("operationId"));

        record.setRequestedModelKey(job.getString("requestedModelKey"));
        record.setResolvedModelKey(job.getString("resolvedModelKey"));
        record.setModelTier(job.getString("resolvedTier"));
        record.setUsedFallback(job.getBoolean("usedFallback"));
        record.setFallbackReason(job.getString("fallbackReason"));
        record.setCreditCost(intValue(job.get("creditCost")));
        record.setCreditTransactionId(job.getString("creditTransactionId"));
        if (record.getResolvedModelKey() == null) {
            missing.add("model");
        }
        // The provider and provider model id were never stored per job, and the registry may have changed since.
        missing.add("provider");

        record.setStatus(status(job.getString("status")));
        record.setErrorMessage(AnalyticsErrors.sanitize(job.getString("errorMessage")));
        record.setTransportAttempts(intValue(job.get("attempt")));

        Instant queuedAt = instant(job.get("queuedAt"));
        Instant startedAt = instant(job.get("startedAt"));
        Instant completedAt = instant(job.get("completedAt"));
        record.setQueuedAt(queuedAt);
        record.setStartedAt(startedAt);
        record.setCompletedAt(completedAt);
        record.setCreatedAt(queuedAt != null ? queuedAt : objectIdTime(job.get("_id")));
        record.setQueueWaitMs(between(queuedAt, startedAt));
        Long durationMs = longValue(job.get("durationMs"));
        record.setExecutionMs(durationMs != null ? durationMs : between(startedAt, completedAt));
        record.setTotalMs(between(queuedAt, completedAt));
        if (record.getExecutionMs() == null) {
            missing.add("latency");
        }

        Document usage = job.get("usage", Document.class);
        boolean local = "OPEN_SOURCE".equals(record.getModelTier());
        Usage usageTotals = usage == null ? null : usage(usage);
        record.setUsageTotals(usageTotals);
        record.setAttemptCount(usage == null ? null : intValue(usage.get("providerCalls")));
        boolean tokensReported = usageTotals != null && usageTotals.getUsageBasis() == UsageBasis.PROVIDER_REPORTED;
        if (!tokensReported) {
            missing.add("usage");
        }

        Cost cost = legacyCost(usage == null ? null : doubleValue(usage.get("estimatedCostUsd")), tokensReported, local);
        record.setCostTotals(cost);
        record.setTotalCost(cost.getTotalCost());
        if (cost.getCostBasis() == CostBasis.UNAVAILABLE) {
            missing.add("cost");
        }

        missing.add("attempts");
        record.setMissingFields(missing);
        record.setDataCompleteness(DataCompleteness.HISTORICAL_PARTIAL);
        record.setUpdatedAt(now);
        return record;
    }

    /** A historical record for one chat {@code ai_responses} document (chat never created a job). */
    public static AiRequestAnalytics fromChatResponse(Document response, Instant now) {
        AiRequestAnalytics record = new AiRequestAnalytics();
        List<String> missing = new ArrayList<>();
        String responseId = string(response.get("_id"));
        record.setId(chatRecordId(responseId));
        record.setSource(AnalyticsSource.BACKFILL_AI_RESPONSE);
        record.setRequestType(AnalyticsRequestType.TEXT_TO_TEXT);
        record.setOperationContext(OperationContext.CHAT);
        record.setCorrelationType("chat");
        Boolean success = response.getBoolean("success");
        record.setStatus(success == null ? null : success ? AnalyticsStatus.SUCCEEDED : AnalyticsStatus.FAILED);

        Instant created = instant(response.get("createdAt"));
        record.setCreatedAt(created != null ? created : objectIdTime(response.get("_id")));

        Document data = response.get("responseData", Document.class);
        Usage usage = new Usage();
        if (data != null) {
            record.setFinalProviderModelId(data.getString("model"));
            usage.setInputTokens(longValue(data.get("promptTokens")));
            usage.setOutputTokens(longValue(data.get("completionTokens")));
            usage.setTotalTokens(longValue(data.get("totalTokens")));
            usage.setCachedInputTokens(longValue(data.get("cachedTokens")));
            usage.setReasoningTokens(longValue(data.get("thoughtsTokens")));
            Object content = data.get("content");
            usage.setOutputChars(content instanceof String text ? text.length() : null);
        }
        boolean tokens = usage.getInputTokens() != null || usage.getOutputTokens() != null || usage.getTotalTokens() != null;
        usage.setUsageBasis(tokens ? UsageBasis.PROVIDER_REPORTED : UsageBasis.NOT_REPORTED);
        record.setUsageTotals(usage);
        record.setAttemptCount(1);
        if (!tokens) {
            missing.add("usage");
        }
        // Chat recorded no registry model key and no cost; the rates in force at the time are unknown.
        missing.add("model");
        Cost cost = new Cost();
        cost.setCostBasis(CostBasis.UNAVAILABLE);
        cost.setInfraCostBasis(CostBasis.UNAVAILABLE);
        cost.setFormula("No cost was recorded for historical chat requests");
        record.setCostTotals(cost);
        missing.add("cost");
        missing.add("latency");
        missing.add("attempts");

        OutputRef ref = new OutputRef();
        ref.setKind("TEXT");
        ref.setCollection("ai_responses");
        ref.setId(responseId);
        record.setOutputRef(ref);

        record.setMissingFields(missing);
        record.setDataCompleteness(DataCompleteness.HISTORICAL_PARTIAL);
        record.setUpdatedAt(now);
        return record;
    }

    public static String chatRecordId(String responseId) {
        return "ai-response-" + responseId;
    }

    /** Where a historical request's result can still be looked up (resolved, with provenance, at read time). */
    public static OutputRef historicalOutputRef(OperationContext context, String jobId, String correlationId) {
        OutputRef ref = new OutputRef();
        switch (context) {
            case RECIPE_FLOW_GENERATION, RECIPE_FLOW_EDIT -> {
                if (jobId == null) return null;
                ref.setKind("JSON");
                ref.setCollection("process_generation_job");
                ref.setId(jobId);
            }
            case IMAGE_PROMPT_GENERATION -> {
                if (correlationId == null) return null;
                ref.setKind("TEXT");
                ref.setCollection("visualization_asset");
                ref.setId(correlationId);
            }
            case STEP_IMAGE_GENERATION, GLOBAL_ASSET_IMAGE -> {
                if (correlationId == null) return null;
                ref.setKind("IMAGE");
                ref.setCollection("visualization_asset");
                ref.setId(correlationId);
            }
            case STEP_NARRATION -> {
                if (correlationId == null) return null;
                int token = correlationId.indexOf('|');
                ref.setKind("AUDIO");
                ref.setCollection("step_narration");
                ref.setId(token >= 0 ? correlationId.substring(0, token) : correlationId);
            }
            default -> {
                return null;
            }
        }
        return ref;
    }

    static Usage usage(Document usage) {
        Usage result = new Usage();
        long prompt = orZero(longValue(usage.get("promptTokens")));
        long completion = orZero(longValue(usage.get("completionTokens")));
        long total = orZero(longValue(usage.get("totalTokens")));
        if (prompt + completion + total == 0) {
            // Indistinguishable from "nothing reported" in the legacy accumulator.
            result.setUsageBasis(UsageBasis.NOT_REPORTED);
            return result;
        }
        result.setInputTokens(prompt);
        result.setCachedInputTokens(longValue(usage.get("cachedTokens")));
        result.setOutputTokens(completion);
        result.setReasoningTokens(longValue(usage.get("thoughtsTokens")));
        result.setTotalTokens(total);
        result.setUsageBasis(UsageBasis.PROVIDER_REPORTED);
        return result;
    }

    static Cost legacyCost(Double estimatedCostUsd, boolean tokensReported, boolean local) {
        Cost cost = new Cost();
        cost.setCurrency("USD");
        cost.setInfraCostBasis(CostBasis.UNAVAILABLE);
        if (local) {
            cost.setDirectCost(0d);
            cost.setTotalCost(0d);
            cost.setCostBasis(CostBasis.CALCULATED);
            cost.setFormula("Local engine: no provider charge; compute cost not recorded");
            return cost;
        }
        if (estimatedCostUsd == null || !tokensReported) {
            cost.setCostBasis(CostBasis.UNAVAILABLE);
            cost.setFormula(estimatedCostUsd == null
                    ? "No cost was recorded for this request"
                    : "Recorded estimate ignored: it was computed from token counts that were never reported");
            return cost;
        }
        cost.setDirectCost(estimatedCostUsd);
        cost.setTotalCost(estimatedCostUsd);
        cost.setCostBasis(CostBasis.ESTIMATED);
        cost.setPricingVersion(LEGACY_PRICING_VERSION);
        cost.setFormula("Estimate recorded at request time (tokens x configured list prices); the rate version was not recorded");
        return cost;
    }

    static AnalyticsStatus status(String legacy) {
        if (legacy == null) {
            return null;
        }
        return switch (legacy) {
            case "COMPLETED" -> AnalyticsStatus.SUCCEEDED;
            case "FAILED" -> AnalyticsStatus.FAILED;
            case "CANCELLED" -> AnalyticsStatus.CANCELLED;
            case "PROCESSING" -> AnalyticsStatus.RUNNING;
            case "QUEUED" -> AnalyticsStatus.QUEUED;
            default -> null;
        };
    }

    static AnalyticsRequestType requestType(String capability) {
        if (capability == null) {
            return AnalyticsRequestType.UNKNOWN;
        }
        return switch (capability) {
            case "TEXT_TO_TEXT" -> AnalyticsRequestType.TEXT_TO_TEXT;
            case "TEXT_TO_IMAGE" -> AnalyticsRequestType.TEXT_TO_IMAGE;
            case "TEXT_TO_SPEECH" -> AnalyticsRequestType.TEXT_TO_AUDIO;
            default -> AnalyticsRequestType.UNKNOWN;
        };
    }

    static Instant instant(Object value) {
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof Instant instant) return instant;
        return null;
    }

    static Instant objectIdTime(Object id) {
        if (id instanceof ObjectId objectId) return objectId.getDate().toInstant();
        if (id instanceof String text && ObjectId.isValid(text)) return new ObjectId(text).getDate().toInstant();
        return null;
    }

    private static Long between(Instant from, Instant to) {
        return from == null || to == null ? null : Math.max(0, Duration.between(from, to).toMillis());
    }

    static String string(Object value) {
        return value == null ? null : value.toString();
    }

    static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    static Integer intValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    static Double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
