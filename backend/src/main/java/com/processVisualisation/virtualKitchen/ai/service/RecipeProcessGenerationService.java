package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationStage;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.routing.FallbackReason;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedRecipeProcessDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidationResult;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Orchestrates AI-driven generation of a semantic Process structure (MAIN
 * process + subprocesses) from free-form recipe text, using the AI queue,
 * model selection and retry-with-feedback against
 * {@link RecipeProcessGenerationPromptBuilder}'s schema and
 * {@link RecipeProcessGenerationValidator}. It never persists the result itself — the generated structure is only ever
 * returned to the frontend, which loads it into the current Recipe working
 * session for the user to review/edit before an explicit Save.
 */
@Service
public class RecipeProcessGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeProcessGenerationService.class);

    private final AiRequestQueueService queueService;
    private final AiClientResolver clientResolver;
    private final RecipeProcessGenerationPromptBuilder promptBuilder;
    private final RecipeProcessGenerationValidator validator;
    private final IAIResponseService aiResponseService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeProcessGenerationService(
            AiRequestQueueService queueService,
            AiClientResolver clientResolver,
            RecipeProcessGenerationPromptBuilder promptBuilder,
            RecipeProcessGenerationValidator validator,
            IAIResponseService aiResponseService
    ) {
        this.queueService = queueService;
        this.clientResolver = clientResolver;
        this.promptBuilder = promptBuilder;
        this.validator = validator;
        this.aiResponseService = aiResponseService;
    }

    public RecipeProcessGenerationResultDTO generate(Long userId, String recipeText, String clientRequestId) {
        return generate(userId, recipeText, clientRequestId, stage -> { });
    }

    /**
     * Same as {@link #generate(Long, String, String)}, but additionally reports the generation
     * pipeline's internal progress milestones via {@code onStage} — used by
     * {@link RecipeProcessGenerationJobService} to back a client-pollable job's progress percentage.
     */
    public RecipeProcessGenerationResultDTO generate(
            Long userId, String recipeText, String clientRequestId, Consumer<RecipeProcessGenerationStage> onStage
    ) {
        onStage.accept(RecipeProcessGenerationStage.BUILDING_PROMPT);

        AiRequestOutcome<RecipeProcessGenerationResultDTO> outcome = queueService.executeBounded(
                userId,
                AiCapability.TEXT_TO_TEXT,
                null,
                clientRequestId,
                "process-generation",
                null,
                selection -> runAttempts(recipeText, selection, onStage)
        );

        RecipeProcessGenerationResultDTO result = outcome.value();
        ModelSelectionOutcome selection = outcome.selection();
        result.setModelUsed(selection.model().getKey());
        result.setModelTier(selection.model().getTier().name());
        result.setUsedFallback(selection.usedFallback());
        result.setFallbackReason(selection.fallbackReason() == FallbackReason.NONE ? null : selection.fallbackReason().name());
        return result;
    }

    private RecipeProcessGenerationResultDTO runAttempts(
            String recipeText, ModelSelectionOutcome selection, Consumer<RecipeProcessGenerationStage> onStage
    ) {
        AIClient client = clientResolver.resolveTextClient(selection.model());
        String modelId = selection.model().getProviderModelId();

        onStage.accept(RecipeProcessGenerationStage.CALLING_MODEL);
        AttemptResult firstAttempt = runAttempt(client, modelId, recipeText, null);
        onStage.accept(RecipeProcessGenerationStage.VALIDATING_RESPONSE);
        if (firstAttempt.valid()) {
            return firstAttempt.result();
        }

        logger.info("Process generation validation failed on first attempt, retrying once. errors={}", firstAttempt.errors());
        onStage.accept(RecipeProcessGenerationStage.RETRYING);
        AttemptResult secondAttempt = runAttempt(client, modelId, recipeText, firstAttempt);
        if (secondAttempt.valid()) {
            return secondAttempt.result();
        }

        String errorMessage = String.join("; ", secondAttempt.errors());
        logger.warn("Process generation failed after retry. errors={}", errorMessage);
        throw new RecipeProcessAiException("Unable to generate a valid recipe process: " + errorMessage);
    }

    private AttemptResult runAttempt(AIClient client, String modelId, String userPrompt, AttemptResult prevAttempt) {
        String refinedPrompt = prevAttempt != null
                ? promptBuilder.buildRetryPrompt(userPrompt, prevAttempt.rawContent(), prevAttempt.errors())
                : promptBuilder.buildInitialPrompt(userPrompt);

        AIRequest request = AIRequest.builder()
                .model(modelId)
                .systemPrompt(promptBuilder.buildSystemPrompt())
                .userPrompt(refinedPrompt)
                .temperature(0.1d)
                .maxTokens(10000)
                .build();

        long startNs = System.nanoTime();
        AIResponse response = client.chat(request);
        long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
        String content = response == null ? null : response.getContent();

        if (!StringUtils.hasText(content)) {
            List<String> errors = List.of("AI response content is empty");
            persistAIResponse(userPrompt, response, false, durationMs, errors);
            return AttemptResult.invalid(content, errors);
        }

        try {
            JsonNode root = parseJson(content);
            GeneratedRecipeProcessDTO mainProcess = objectMapper.treeToValue(root.path("mainProcess"), GeneratedRecipeProcessDTO.class);
            List<GeneratedRecipeProcessDTO> subprocesses = root.path("subprocesses").isMissingNode()
                    ? List.of()
                    : objectMapper.convertValue(root.path("subprocesses"), objectMapper.getTypeFactory().constructCollectionType(List.class, GeneratedRecipeProcessDTO.class));

            ProcessValidationResult validationResult = validator.validate(mainProcess, subprocesses);
            if (!validationResult.isValid()) {
                persistAIResponse(userPrompt, response, false, durationMs, validationResult.getErrors());
                return AttemptResult.invalid(content, validationResult.getErrors());
            }

            RecipeProcessGenerationResultDTO generated = RecipeProcessGenerationResultDTO.builder()
                    .mainProcess(mainProcess)
                    .subprocesses(subprocesses)
                    .build();
            persistAIResponse(userPrompt, response, true, durationMs, List.of());
            return AttemptResult.valid(content, generated);
        } catch (Exception ex) {
            List<String> errors = List.of("Invalid JSON format: " + ex.getMessage());
            persistAIResponse(userPrompt, response, false, durationMs, errors);
            return AttemptResult.invalid(content, errors);
        }
    }

    private void persistAIResponse(String userPrompt, AIResponse response, boolean success, long durationMs, List<String> errors) {
        try {
            AIResponseRecordDTO record = new AIResponseRecordDTO();
            record.setContext("process-generation");
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
            logger.error("Failed to persist AI response for process generation", ex);
        }
    }

    private JsonNode parseJson(String content) throws JsonProcessingException {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException firstEx) {
            return objectMapper.readTree(stripCodeFences(content));
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

    private record AttemptResult(boolean valid, String rawContent, RecipeProcessGenerationResultDTO result, List<String> errors) {
        static AttemptResult valid(String rawContent, RecipeProcessGenerationResultDTO result) {
            return new AttemptResult(true, rawContent, result, List.of());
        }

        static AttemptResult invalid(String rawContent, List<String> errors) {
            return new AttemptResult(false, rawContent, null, errors);
        }
    }
}
