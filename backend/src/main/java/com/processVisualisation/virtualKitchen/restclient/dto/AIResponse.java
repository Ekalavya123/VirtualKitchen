package com.processVisualisation.virtualKitchen.restclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Models the parsed result of a chat/completion request from any AIClient
 * provider (Gemini, OpenAI, Ollama), normalizing the generated content,
 * resolved model, token usage, finish reason, and raw response body into a
 * provider-agnostic shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIResponse {

    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    /** Prompt tokens served from the provider's context cache (implicit or explicit); null when not reported. */
    private Integer cachedTokens;
    /** Reasoning tokens billed as output but not included in {@link #completionTokens} (Gemini); null when not reported. */
    private Integer thoughtsTokens;
    private String finishReason;
    private String rawResponse;
}
