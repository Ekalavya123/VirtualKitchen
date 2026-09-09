package com.processVisualisation.virtualKitchen.restclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Models an outbound chat/completion request sent to any AIClient
 * provider (Gemini, OpenAI, Ollama), carrying the system/user prompts,
 * target model, and generation parameters that each client maps onto its
 * own provider-specific request payload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIRequest {

    private String systemPrompt;
    private String userPrompt;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private String responseFormat;
}
