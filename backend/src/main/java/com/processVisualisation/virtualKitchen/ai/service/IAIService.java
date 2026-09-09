package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

/**
 * Service contract for interacting with the underlying AI/LLM provider to
 * answer free-form chat prompts.
 */
public interface IAIService {

    /**
     * Sends a prompt to the configured AI provider and returns its response.
     *
     * @param prompt the free-form user prompt
     * @return the AI provider's response, including generated content and usage metadata
     */
    AIResponse chat(String prompt);
}
