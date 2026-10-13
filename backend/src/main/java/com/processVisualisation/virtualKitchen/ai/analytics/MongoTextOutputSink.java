package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes one {@code ai_responses} document per text provider call, in the shape that collection already uses
 * ({@code context}, {@code input}, {@code success}, {@code responseData}), plus the analytics links. Callers that
 * used to write their own record for the call (process generation and editing) now complete this one instead.
 */
@Component
public class MongoTextOutputSink implements TextOutputSink {

    private final MongoTemplate mongoTemplate;

    public MongoTextOutputSink(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String save(String analyticsRequestId, String attemptId, OperationContext context, AiCallRecord call) {
        AIResponseDocument doc = new AIResponseDocument();
        doc.setContext(contextName(context));
        doc.setInput(call.getInputText());
        doc.setSuccess(call.getOutputText() != null && !call.getOutputText().isBlank());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", call.getOutputText());
        data.put("model", call.getReportedModel());
        data.put("promptTokens", call.getInputTokens());
        data.put("completionTokens", call.getOutputTokens());
        data.put("totalTokens", call.getTotalTokens());
        data.put("cachedTokens", call.getCachedInputTokens());
        data.put("thoughtsTokens", call.getReasoningTokens());
        data.put("finishReason", call.getFinishReason());
        data.put("durationMs", call.getDurationMs());
        doc.setResponseData(data);
        doc.setAnalyticsRequestId(analyticsRequestId);
        doc.setAttemptId(attemptId);
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return mongoTemplate.insert(doc).getId();
    }

    /** The {@code context} values {@code ai_responses} already uses, so existing queries keep matching. */
    static String contextName(OperationContext context) {
        return switch (context) {
            case RECIPE_FLOW_GENERATION -> "process-generation";
            case RECIPE_FLOW_EDIT -> "process-edit";
            case IMAGE_PROMPT_GENERATION -> "visualization-prompt";
            case CHAT -> "chat";
            default -> context.name().toLowerCase().replace('_', '-');
        };
    }
}
