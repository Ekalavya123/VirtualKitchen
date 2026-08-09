package com.processVisualisation.virtualKitchen.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.client.AIClient;
import com.processVisualisation.virtualKitchen.ai.dto.AIRequest;
import com.processVisualisation.virtualKitchen.ai.dto.AIResponse;
import com.processVisualisation.virtualKitchen.dto.RecipeExecutionEdgeDTO;
import com.processVisualisation.virtualKitchen.dto.RecipeExecutionStepDTO;
import com.processVisualisation.virtualKitchen.dto.RecipeFlowGenerationResponseDTO;
import com.processVisualisation.virtualKitchen.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.service.recipe.RecipeFlowPromptBuilder;
import com.processVisualisation.virtualKitchen.service.recipe.RecipeFlowValidationResult;
import com.processVisualisation.virtualKitchen.service.recipe.RecipeValidator;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.processVisualisation.virtualKitchen.dto.AIResponseRecordDTO;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecipeGenerationService {

    private static final TypeReference<List<RecipeExecutionStepDTO>> LIST_OF_STEP_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<List<RecipeExecutionEdgeDTO>> LIST_OF_EDGE_TYPE = new TypeReference<>() {
    };

    private final AIClient aiClient;
    private final RecipeFlowPromptBuilder promptBuilder;
    private final RecipeValidator recipeValidator;
    private final IAIResponseService aiResponseService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(RecipeGenerationService.class);

    public RecipeGenerationService(
            AIClient aiClient,
            RecipeFlowPromptBuilder promptBuilder,
            RecipeValidator recipeValidator,
            IAIResponseService aiResponseService
    ) {
        this.aiClient = aiClient;
        this.promptBuilder = promptBuilder;
        this.recipeValidator = recipeValidator;
        this.aiResponseService = aiResponseService;
    }

    public RecipeFlowGenerationResponseDTO generateFlow(String recipeText) {
        System.out.println("[RECIPE-GEN] Start generate flow");
        AttemptResult firstAttempt = runAttempt(recipeText, null);
        if (firstAttempt.valid()) {
            return firstAttempt.response();
        }

        System.out.println("[RECIPE-GEN] Validation failed on first attempt. Retrying once.");
        AttemptResult secondAttempt = runAttempt(recipeText, firstAttempt);
        if (secondAttempt.valid()) {
            return secondAttempt.response();
        }

        String errorMessage = String.join("; ", secondAttempt.errors());
        System.out.println("[RECIPE-GEN] Failed after retry. errors=" + errorMessage);
        throw new RecipeFlowGenerationException("Unable to generate valid recipe flow: " + errorMessage);
    }

    private AttemptResult runAttempt(String userPrompt, AttemptResult prevAttempt) {
        String refinedPrompt = prevAttempt != null ? promptBuilder.buildRetryPrompt(userPrompt, prevAttempt.rawContent(), prevAttempt.errors()) : promptBuilder.buildInitialPrompt(userPrompt);
        AIRequest request = AIRequest.builder()
                .systemPrompt(promptBuilder.buildSystemPrompt())
                .userPrompt(refinedPrompt)
                .temperature(0.1d)
                .maxTokens(3000)
                .build();

        long startNs = System.nanoTime();
        AIResponse response = aiClient.chat(request);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        String content = response == null ? null : response.getContent();

        if (!StringUtils.hasText(content)) {
            AttemptResult invalidResult = AttemptResult.invalid(content, List.of("AI response content is empty"));
            // persist attempt (even failures)
            persistAIResponse(userPrompt, response, invalidResult.valid, durationMs, List.of("AI response content is empty"));
            return invalidResult;
        }

        try {
            JsonNode root = parseJson(content);
            JsonNode stepsNode = root.path("steps");
            JsonNode edgesNode = root.path("edges");

            if (!stepsNode.isArray() || !edgesNode.isArray()) {
                return AttemptResult.invalid(content, List.of("Top-level JSON must contain arrays: steps and edges"));
            }

             List<RecipeExecutionStepDTO> steps = objectMapper.convertValue(stepsNode, LIST_OF_STEP_TYPE);
             List<RecipeExecutionEdgeDTO> edges = objectMapper.convertValue(edgesNode, LIST_OF_EDGE_TYPE);

             normalizeExecutionDataTypes(steps, edges);

             RecipeFlowValidationResult validationResult = recipeValidator.validate(steps, edges);
            if (!validationResult.isValid()) {
                AttemptResult invalidResult = AttemptResult.invalid(content, validationResult.getErrors());
                persistAIResponse(userPrompt, response, invalidResult.valid, durationMs, validationResult.getErrors());
                return invalidResult;
            }

            RecipeFlowGenerationResponseDTO generated = new RecipeFlowGenerationResponseDTO(steps, edges);
            AttemptResult validResult = AttemptResult.valid(content, generated);
            persistAIResponse(userPrompt, response, validResult.valid, durationMs, List.of());
            return validResult;
        } catch (JsonProcessingException ex) {
            AttemptResult invalidResult = AttemptResult.invalid(content, List.of("Invalid JSON format: " + ex.getOriginalMessage()));
            persistAIResponse(userPrompt, response, invalidResult.valid, durationMs, List.of("Invalid JSON format: " + ex.getOriginalMessage()));
            return invalidResult;
        }
    }

    private void persistAIResponse(String userPrompt, AIResponse response, boolean success, long durationMs, List<String> errors) {
        try {
            AIResponseRecordDTO record = new AIResponseRecordDTO();
            record.setContext("flow-generation");
            record.setInput(userPrompt);
            record.setSuccess(success);

            Map<String, Object> respData = new LinkedHashMap<>();
            respData.put("content", response == null ? null : response.getContent());
            respData.put("model", response == null ? null : response.getModel());
            respData.put("promptTokens", response == null ? null : response.getPromptTokens());
            respData.put("completionTokens", response == null ? null : response.getCompletionTokens());
            respData.put("totalTokens", response == null ? null : response.getTotalTokens());
            respData.put("finishReason", response == null ? null : response.getFinishReason());
            respData.put("rawResponse", response == null ? null : response.getRawResponse());
            respData.put("durationMs", durationMs);
            if (errors != null && !errors.isEmpty()) respData.put("errors", errors);

            record.setResponseData(respData);
            aiResponseService.save(record);
        } catch (Exception ex) {
            logger.error("Failed to persist AI response for flow generation", ex);
        }
    }

    private JsonNode parseJson(String content) throws JsonProcessingException {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException firstEx) {
            String sanitized = stripCodeFences(content);
            return objectMapper.readTree(sanitized);
        }
    }

     private String stripCodeFences(String content) {
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

     private void normalizeExecutionDataTypes(
             List<RecipeExecutionStepDTO> steps,
             List<RecipeExecutionEdgeDTO> edges
     ) {
         for (RecipeExecutionStepDTO step : steps) {
             if (step == null) continue;
             step.setId(toStringValue(step.getId()));
             step.setNodeType(toStringValue(step.getNodeType()));
             step.setData(normalizeDataMap(step.getData()));
         }

         for (RecipeExecutionEdgeDTO edge : edges) {
             if (edge == null) continue;
             edge.setFrom(toStringValue(edge.getFrom()));
             edge.setTo(toStringValue(edge.getTo()));
             edge.setLabel(toStringValue(edge.getLabel()));
         }
     }

    private Map<String, Object> normalizeDataMap(Map<String, Object> data) {
        if (data == null) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            normalized.put(entry.getKey(), toStringValue(entry.getValue()));
        }
        return normalized;
    }

    private String toStringValue(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }

    private record AttemptResult(
            boolean valid,
            String rawContent,
            RecipeFlowGenerationResponseDTO response,
            List<String> errors
    ) {
        static AttemptResult valid(String rawContent, RecipeFlowGenerationResponseDTO response) {
            return new AttemptResult(true, rawContent, response, List.of());
        }

        static AttemptResult invalid(String rawContent, List<String> errors) {
            return new AttemptResult(false, rawContent, null, errors);
        }
    }
}
