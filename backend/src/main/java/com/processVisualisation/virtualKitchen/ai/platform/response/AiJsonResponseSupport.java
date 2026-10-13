package com.processVisualisation.virtualKitchen.ai.platform.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.platform.response.dto.AiResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.platform.usage.AiCallListener;
import com.processVisualisation.virtualKitchen.ai.platform.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plumbing shared by the structured-JSON AI pipelines (process generation and process editing): lenient parsing of
 * the model's JSON content and the best-effort analytics record of every attempt.
 */
@Component
public class AiJsonResponseSupport {

    private static final Logger logger = LoggerFactory.getLogger(AiJsonResponseSupport.class);

    private final AiResponseService aiResponseService;
    /** Lenient on unknown keys: a harmless extra field must not cost a full regeneration retry. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public AiJsonResponseSupport(AiResponseService aiResponseService) {
        this.aiResponseService = aiResponseService;
    }

    /** Parses {@code content} into {@code type}, tolerating a markdown code fence around the JSON. */
    public <T> T readValue(String content, Class<T> type) throws JsonProcessingException {
        return objectMapper.treeToValue(parseJson(content), type);
    }

    public void persistAIResponse(String context, String userPrompt, AiResponse response, boolean success, long durationMs, List<String> errors) {
        // Tell the request's analytics whether this call's output was accepted (a no-op outside a tracked request).
        AiUsageMeter.reportValidation(success, errors);
        try {
            // The analytics recorder has already stored this call's output; complete that record rather than
            // writing a second one for the same call.
            AiCallListener analytics = AiUsageMeter.currentListener();
            String recordedOutputId = analytics == null ? null : analytics.lastTextOutputId();
            if (recordedOutputId != null) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("rawResponse", response == null ? null : response.getRawResponse());
                extra.put("durationMs", durationMs);
                if (errors != null && !errors.isEmpty()) extra.put("errors", errors);
                if (aiResponseService.annotate(recordedOutputId, context, success, extra).isPresent()) {
                    return;
                }
            }

            AiResponseRecordDTO record = new AiResponseRecordDTO();
            record.setContext(context);
            record.setInput(userPrompt);
            record.setSuccess(success);

            Map<String, Object> respData = new LinkedHashMap<>();
            respData.put("content", response == null ? null : response.getContent());
            respData.put("model", response == null ? null : response.getModel());
            respData.put("promptTokens", response == null ? null : response.getPromptTokens());
            respData.put("completionTokens", response == null ? null : response.getCompletionTokens());
            respData.put("totalTokens", response == null ? null : response.getTotalTokens());
            respData.put("cachedTokens", response == null ? null : response.getCachedTokens());
            respData.put("thoughtsTokens", response == null ? null : response.getThoughtsTokens());
            respData.put("finishReason", response == null ? null : response.getFinishReason());
            respData.put("rawResponse", response == null ? null : response.getRawResponse());
            respData.put("durationMs", durationMs);
            if (errors != null && !errors.isEmpty()) respData.put("errors", errors);

            record.setResponseData(respData);
            aiResponseService.save(record);
        } catch (Exception ex) {
            // Best-effort analytics record; the AI operation itself is unaffected.
            logger.warn("event=ai_response_persist_failed context={} errorType={}",
                    context, ex.getClass().getSimpleName(), ex);
        }
    }

    private JsonNode parseJson(String content) throws JsonProcessingException {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException firstEx) {
            return objectMapper.readTree(stripCodeFences(content));
        }
    }

    private static String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return trimmed.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return content;
    }
}
