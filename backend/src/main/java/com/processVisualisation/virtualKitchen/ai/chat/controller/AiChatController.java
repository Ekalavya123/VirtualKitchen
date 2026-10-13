package com.processVisualisation.virtualKitchen.ai.chat.controller;

import com.processVisualisation.virtualKitchen.ai.chat.AiChatService;
import com.processVisualisation.virtualKitchen.ai.chat.dto.AiChatRequestDTO;
import com.processVisualisation.virtualKitchen.ai.chat.dto.AiChatResponseDTO;
import com.processVisualisation.virtualKitchen.ai.platform.response.AiResponseService;
import com.processVisualisation.virtualKitchen.ai.platform.response.dto.AiResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller exposing general-purpose AI chat endpoints under {@code /api/ai}.
 * Delegates prompt handling to {@link AiChatService} and persists a record of each
 * interaction (including failures) via {@link AiResponseService} for analytics
 * and training purposes.
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiService;
    private final AiResponseService aiResponseService;
    private static final Logger logger = LoggerFactory.getLogger(AiChatController.class);

    public AiChatController(AiChatService aiService, AiResponseService aiResponseService) {
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
    public AiChatResponseDTO chat(@Valid @RequestBody AiChatRequestDTO request) {
        AiResponse response = aiService.chat(request.getPrompt());
        if (response.getAnalyticsRequestId() != null) {
            // Already recorded, output included, by the AI analytics recorder.
            return new AiChatResponseDTO(response.getContent());
        }
        // Persist the response for analytics/training (store even failures)
        try {
            AiResponseRecordDTO record = new AiResponseRecordDTO();
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
            logger.warn("event=ai_response_persist_failed context=chat errorType={}", ex.getClass().getSimpleName(), ex);
        }

        return new AiChatResponseDTO(response.getContent());
    }
}
