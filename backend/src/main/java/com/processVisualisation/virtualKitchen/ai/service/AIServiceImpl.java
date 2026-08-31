package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.config.OpenAIProperties;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AIServiceImpl implements IAIService {

    private final AIClient aiClient;
    private final OpenAIProperties openAIProperties;
    private final GeminiProperties geminiProperties;
    private final String provider;

    public AIServiceImpl(AIClient aiClient,
                         OpenAIProperties openAIProperties,
                         ObjectProvider<GeminiProperties> geminiPropertiesProvider,
                         @Value("${ai.provider:gemini}") String provider) {
        this.aiClient = aiClient;
        this.openAIProperties = openAIProperties;
        this.geminiProperties = geminiPropertiesProvider.getIfAvailable();
        this.provider = provider == null ? "gemini" : provider;
    }

    @Override
    public AIResponse chat(String prompt) {
        String defaultModel = openAIProperties.getDefaultModel();
        if ("gemini".equalsIgnoreCase(provider) && geminiProperties != null && geminiProperties.getDefaultModel() != null) {
            defaultModel = geminiProperties.getDefaultModel();
        }

        AIRequest request = AIRequest.builder()
                .systemPrompt("You are a helpful assistant for Virtual Kitchen.")
                .userPrompt(prompt)
                .model(defaultModel)
                .temperature(0.2d)
                .maxTokens(300)
                .build();

        return aiClient.chat(request);
    }
}
