package com.processVisualisation.virtualKitchen.restclient.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "ai.ollama")
public class OllamaProperties {

    private String baseUrl;
    private String chatEndpoint;
    private String defaultModel;
    private Long timeoutMs;
}