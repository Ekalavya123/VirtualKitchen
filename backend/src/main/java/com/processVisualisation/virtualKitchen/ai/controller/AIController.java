package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.ai.dto.AIChatRequestDTO;
import com.processVisualisation.virtualKitchen.ai.dto.AIChatResponseDTO;
import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.service.IAIService;
import com.processVisualisation.virtualKitchen.ai.service.IAIResponseService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller exposing general-purpose AI chat endpoints under {@code /api/ai}.
 * Delegates prompt handling to {@link IAIService} and persists a record of each
 * interaction (including failures) via {@link IAIResponseService} for analytics
 * and training purposes.
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final IAIService aiService;
    private final IAIResponseService aiResponseService;
    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    public AIController(IAIService aiService, IAIResponseService aiResponseService) {
        this.aiService = aiService;
        this.aiResponseService = aiResponseService;
    }

    /**
     * Handles a chat prompt by forwarding it to the configured AI service and
     * returning the generated content. The full AI response (including token
     * usage and raw payload) is persisted asynchronously to the response log;
     * persistence failures are logged but do not fail the request.
     *
     * @param request the validated chat request containing the user prompt
     * @return the chat response wrapping the AI-generated content
     */
    @PostMapping("/chat")
    public AIChatResponseDTO chat(@Valid @RequestBody AIChatRequestDTO request) {
        AIResponse response = aiService.chat(request.getPrompt());
        // Persist the response for analytics/training (store even failures)
        try {
            AIResponseRecordDTO record = new AIResponseRecordDTO();
            record.setContext("chat");
            record.setInput(request.getPrompt());
            record.setSuccess(response.getContent() != null && !response.getContent().isEmpty());
            java.util.Map<String, Object> respData = new java.util.LinkedHashMap<>();
            respData.put("content", response.getContent());
            respData.put("model", response.getModel());
            respData.put("promptTokens", response.getPromptTokens());
            respData.put("completionTokens", response.getCompletionTokens());
            respData.put("totalTokens", response.getTotalTokens());
            respData.put("finishReason", response.getFinishReason());
            respData.put("rawResponse", response.getRawResponse());
            record.setResponseData(respData);
            aiResponseService.save(record);
        } catch (Exception ex) {
            // Don't fail the request if persistent storage fails; just log.
            logger.error("Failed to persist AI response", ex);
        }

        return new AIChatResponseDTO(response.getContent());
    }
}
