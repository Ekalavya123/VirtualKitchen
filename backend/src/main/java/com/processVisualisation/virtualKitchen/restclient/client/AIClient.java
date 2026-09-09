package com.processVisualisation.virtualKitchen.restclient.client;

import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

/**
 * Common contract implemented by every text-generation AI provider client
 * (Gemini, OpenAI, Ollama) used by the Virtual Kitchen application. Callers
 * depend on this abstraction instead of on a specific provider so the active
 * provider can be swapped via configuration.
 */
public interface AIClient {

    /**
     * Sends a chat/completion request to the underlying AI provider and
     * returns the parsed response.
     *
     * @param request the prompt, model, and generation parameters to send
     * @return the provider's response, including generated content and usage metadata
     */
    AIResponse chat(AIRequest request);
}
