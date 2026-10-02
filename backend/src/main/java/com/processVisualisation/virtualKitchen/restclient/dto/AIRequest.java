package com.processVisualisation.virtualKitchen.restclient.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
    /**
     * Optional JSON Schema the response must follow. When set, clients request structured output
     * (Ollama {@code format}, OpenAI {@code json_schema}, Gemini {@code responseJsonSchema}) instead of plain JSON mode.
     */
    private Map<String, Object> responseSchema;
    /** Name reported alongside {@link #responseSchema} where the provider requires one (OpenAI). */
    private String responseSchemaName;
    /**
     * Hint that {@link #systemPrompt} is static and reused across many requests, so a provider with explicit
     * context caching (Gemini {@code cachedContents}) may store it once and reference it instead of re-sending it.
     * Providers that cache prefixes automatically (Ollama, OpenAI) ignore it.
     */
    private boolean cacheSystemPrompt;
}
