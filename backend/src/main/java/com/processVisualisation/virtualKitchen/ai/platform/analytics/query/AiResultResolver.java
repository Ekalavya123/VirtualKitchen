package com.processVisualisation.virtualKitchen.ai.platform.analytics.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Attempt;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.OutputRef;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Fetches a request's AI result on demand, from where the application already keeps it — never from a copy in
 * analytics, and never by regenerating it. Each result says where it came from ({@link Provenance}):
 * <ul>
 *     <li>{@code RETAINED}: the exact output of this request (text kept per call in {@code ai_responses}, or the
 *     storage URL recorded when its image/audio was uploaded).</li>
 *     <li>{@code JOB_RESULT}: a historical generation's result, read from its {@code process_generation_job}.</li>
 *     <li>{@code CURRENT_ASSET} / {@code CURRENT_ASSET_MAY_BE_NEWER}: a historical image/audio/prompt read from the
 *     asset it produced; flagged when the asset changed after the request finished, i.e. it may have been
 *     regenerated since.</li>
 *     <li>{@code REUSED_FROM}: a reused request shows the result of the request that originally produced it.</li>
 * </ul>
 */
@Service
public class AiResultResolver {

    static final int DEFAULT_MAX_CHARS = 20_000;
    public static final String NOT_RETAINED = "Output not retained or unavailable.";

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiResultResolver(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public enum Provenance { RETAINED, JOB_RESULT, CURRENT_ASSET, CURRENT_ASSET_MAY_BE_NEWER, REUSED_FROM }

    public record AttemptSummary(String attemptId, int callIndex, Integer transportAttempt, String purpose,
                                 String status, Boolean validationPassed, String errorCategory, String errorMessage,
                                 boolean hasOutput) {
    }

    public record Metrics(String model, String providerModelId, String status, Long inputTokens, Long outputTokens,
                          Double totalCost, String costBasis, String currency, Boolean costComplete, Long latencyMs,
                          Integer attemptCount, Integer transportAttempts, Boolean validationPassed) {
    }

    public record Result(String requestId, boolean retained, String kind, String content, String url, String mimeType,
                         Long sizeBytes, Long audioDurationMs, Integer imageWidth, Integer imageHeight,
                         boolean truncated, int contentLength, String prompt, Provenance provenance,
                         String provenanceNote, String message, String errorMessage, String attemptId,
                         List<AttemptSummary> attempts, Metrics metrics) {
    }

    public Optional<Result> resolve(String requestId, String attemptId, boolean full, boolean includePrompt) {
        AiRequestAnalytics record = mongoTemplate.findById(requestId, AiRequestAnalytics.class);
        if (record == null) {
            return Optional.empty();
        }
        Builder result = new Builder(record);
        AiRequestAnalytics source = record;
        if (record.getStatus() == AnalyticsStatus.REUSED && record.getReusedFromRequestId() != null) {
            AiRequestAnalytics original = mongoTemplate.findById(record.getReusedFromRequestId(), AiRequestAnalytics.class);
            if (original != null) {
                source = original;
                result.reusedFrom = original.getId();
            }
        }

        OutputRef ref = source.getOutputRef();
        if (attemptId != null) {
            Attempt attempt = source.getAttempts().stream()
                    .filter(a -> attemptId.equals(a.getAttemptId())).findFirst().orElse(null);
            ref = attempt == null || attempt.getOutputId() == null ? null : textRef(attempt.getOutputId());
            result.attemptId = attempt == null ? null : attempt.getAttemptId();
        }
        if (ref != null) {
            fill(result, source, ref, includePrompt);
        }
        if (result.reusedFrom != null && result.retained) {
            result.provenance = Provenance.REUSED_FROM;
            result.provenanceNote = "Served from the artifact store; this is the output of request " + result.reusedFrom;
        }
        return Optional.of(result.build(full ? Integer.MAX_VALUE : DEFAULT_MAX_CHARS));
    }

    private void fill(Builder result, AiRequestAnalytics source, OutputRef ref, boolean includePrompt) {
        String collection = ref.getCollection() == null ? "" : ref.getCollection();
        switch (collection) {
            case "ai_responses" -> {
                Document doc = findById("ai_responses", ref.getId());
                if (doc == null) return;
                Document data = doc.get("responseData", Document.class);
                Object content = data == null ? null : data.get("content");
                if (content instanceof String text && !text.isEmpty()) {
                    result.text(text, looksLikeJson(text) ? "JSON" : "TEXT", Provenance.RETAINED, null);
                }
                if (includePrompt) result.prompt = doc.getString("input");
            }
            case "process_generation_job" -> {
                Document job = findById("process_generation_job", ref.getId());
                Object output = job == null ? null : job.get("result");
                if (output instanceof Document resultDoc) {
                    result.text(pretty(resultDoc), "JSON", Provenance.JOB_RESULT,
                            "The final (validated) result stored on the generation job; per-attempt outputs were not retained.");
                }
            }
            case "visualization_asset" -> {
                if (ref.getUrl() != null) {
                    result.media("IMAGE", ref, Provenance.RETAINED, null);
                    Document asset = findAsset(ref);
                    if (includePrompt && asset != null) result.prompt = asset.getString("imagePrompt");
                    usageDimensions(result, source);
                    return;
                }
                Document asset = findAsset(ref);
                if (asset == null) return;
                Provenance provenance = changedAfter(asset.get("updatedAt"), source.getCompletedAt())
                        ? Provenance.CURRENT_ASSET_MAY_BE_NEWER : Provenance.CURRENT_ASSET;
                String note = provenance == Provenance.CURRENT_ASSET_MAY_BE_NEWER
                        ? "The asset changed after this request finished; it may have been regenerated since." : null;
                if ("TEXT".equals(ref.getKind())) {
                    String prompt = asset.getString("imagePrompt");
                    if (prompt != null) result.text(prompt, "TEXT", provenance, note);
                } else {
                    String url = asset.getString("imageUrl");
                    if (url != null) {
                        result.retained = true;
                        result.kind = "IMAGE";
                        result.url = url;
                        result.provenance = provenance;
                        result.provenanceNote = note;
                    }
                    if (includePrompt) result.prompt = asset.getString("imagePrompt");
                }
            }
            case "step_narration" -> {
                if (ref.getUrl() != null) {
                    result.media("AUDIO", ref, Provenance.RETAINED, null);
                    if (source.getUsageTotals() != null) result.audioDurationMs = source.getUsageTotals().getAudioDurationMs();
                    return;
                }
                Document narration = mongoTemplate.findOne(Query.query(where("narrationKey").is(ref.getId())),
                        Document.class, "step_narration");
                if (narration == null || narration.getString("audioUrl") == null) return;
                Provenance provenance = changedAfter(narration.get("updatedAt"), source.getCompletedAt())
                        ? Provenance.CURRENT_ASSET_MAY_BE_NEWER : Provenance.CURRENT_ASSET;
                result.retained = true;
                result.kind = "AUDIO";
                result.url = narration.getString("audioUrl");
                result.mimeType = narration.getString("mimeType");
                result.sizeBytes = narration.get("sizeBytes") instanceof Number n ? n.longValue() : null;
                result.audioDurationMs = narration.get("durationMs") instanceof Number n ? n.longValue() : null;
                result.provenance = provenance;
                result.provenanceNote = provenance == Provenance.CURRENT_ASSET_MAY_BE_NEWER
                        ? "The narration changed after this request finished; it may have been regenerated since." : null;
            }
            default -> {
            }
        }
    }

    private static void usageDimensions(Builder result, AiRequestAnalytics source) {
        if (source.getUsageTotals() != null) {
            result.imageWidth = source.getUsageTotals().getImageWidth();
            result.imageHeight = source.getUsageTotals().getImageHeight();
        }
    }

    private Document findAsset(OutputRef ref) {
        if (ref.getId() == null) return null;
        // Live records point at the asset's numeric id; historical ones at its visualization key.
        if (ref.getId().matches("\\d+")) {
            Document byId = mongoTemplate.findById(Long.valueOf(ref.getId()), Document.class, "visualization_asset");
            if (byId != null) return byId;
        }
        return mongoTemplate.findOne(Query.query(where("visualizationKey").is(ref.getId())), Document.class, "visualization_asset");
    }

    private Document findById(String collection, String id) {
        if (id == null) return null;
        Object key = ObjectId.isValid(id) ? new ObjectId(id) : id;
        Document doc = mongoTemplate.findOne(Query.query(where("_id").is(key)), Document.class, collection);
        return doc != null || key instanceof String ? doc
                : mongoTemplate.findOne(Query.query(where("_id").is(id)), Document.class, collection);
    }

    static boolean changedAfter(Object updatedAt, Instant completedAt) {
        if (completedAt == null || !(updatedAt instanceof Date date)) {
            return false;
        }
        // A little slack: the asset is written moments after the request completes.
        return date.toInstant().isAfter(completedAt.plusSeconds(120));
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) return false;
        try {
            objectMapper.readTree(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String pretty(Document doc) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(doc.toJson()));
        } catch (Exception e) {
            return doc.toJson();
        }
    }

    private static OutputRef textRef(String id) {
        OutputRef ref = new OutputRef();
        ref.setKind("TEXT");
        ref.setCollection("ai_responses");
        ref.setId(id);
        return ref;
    }

    /** Mutable accumulator for a {@link Result}. */
    private static final class Builder {
        private final AiRequestAnalytics record;
        boolean retained;
        String kind = "NONE";
        String content;
        String url;
        String mimeType;
        Long sizeBytes;
        Long audioDurationMs;
        Integer imageWidth;
        Integer imageHeight;
        String prompt;
        Provenance provenance;
        String provenanceNote;
        String reusedFrom;
        String attemptId;

        Builder(AiRequestAnalytics record) {
            this.record = record;
        }

        void text(String text, String kind, Provenance provenance, String note) {
            this.retained = true;
            this.kind = kind;
            this.content = text;
            this.provenance = provenance;
            this.provenanceNote = note;
        }

        void media(String kind, OutputRef ref, Provenance provenance, String note) {
            this.retained = true;
            this.kind = kind;
            this.url = ref.getUrl();
            this.mimeType = ref.getMimeType();
            this.sizeBytes = ref.getSizeBytes();
            this.provenance = provenance;
            this.provenanceNote = note;
        }

        Result build(int maxChars) {
            int length = content == null ? 0 : content.length();
            boolean truncated = length > maxChars;
            List<AttemptSummary> attempts = new ArrayList<>();
            for (Attempt a : record.getAttempts()) {
                attempts.add(new AttemptSummary(a.getAttemptId(), a.getCallIndex(), a.getTransportAttempt(), a.getPurpose(),
                        a.getStatus() == null ? null : a.getStatus().name(), a.getValidationPassed(), a.getErrorCategory(),
                        a.getErrorMessage(), a.getOutputId() != null));
            }
            AiRequestAnalytics.Usage usage = record.getUsageTotals();
            AiRequestAnalytics.Cost cost = record.getCostTotals();
            Metrics metrics = new Metrics(record.getResolvedModelKey(), record.getFinalProviderModelId(),
                    record.getStatus() == null ? null : record.getStatus().name(),
                    usage == null ? null : usage.getInputTokens(), usage == null ? null : usage.getOutputTokens(),
                    record.getTotalCost(), cost == null || cost.getCostBasis() == null ? null : cost.getCostBasis().name(),
                    cost == null ? null : cost.getCurrency(), cost == null ? null : cost.getComplete(),
                    record.getTotalMs(), record.getAttemptCount(), record.getTransportAttempts(), record.getValidationPassed());
            return new Result(record.getId(), retained, kind, truncated ? content.substring(0, maxChars) : content, url,
                    mimeType, sizeBytes, audioDurationMs, imageWidth, imageHeight, truncated, length, prompt, provenance,
                    provenanceNote, retained ? null : NOT_RETAINED, record.getErrorMessage(), attemptId, attempts, metrics);
        }
    }
}
