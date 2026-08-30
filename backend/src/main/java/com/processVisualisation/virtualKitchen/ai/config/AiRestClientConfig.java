package com.processVisualisation.virtualKitchen.ai.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiRestClientConfig {

    // 1. DRAWTHINGS CLIENT (Resolves your startup crash)
    @Bean("drawThingsRestClient")
    public RestClient drawThingsRestClient(DrawThingsProperties properties) {
        long configuredTimeout = properties.getTimeoutMs() == null ? 60000L : properties.getTimeoutMs();
        return createRestClient(properties.getBaseUrl(), configuredTimeout);
    }

    // 2. GEMINI CLIENT
    @Bean("geminiRestClient")
    public RestClient geminiRestClient(GeminiProperties properties) {
        long configuredTimeout = properties.getTimeoutMs() == null ? 30000L : properties.getTimeoutMs();
        return createRestClient(properties.getBaseUrl(), configuredTimeout);
    }

    // 3. OLLAMA CLIENT
    @Bean("ollamaRestClient")
    public RestClient ollamaRestClient(OllamaProperties properties) {
        long configuredTimeout = properties.getTimeoutMs() == null ? 120000L : properties.getTimeoutMs();
        return createRestClient(properties.getBaseUrl(), configuredTimeout);
    }

    // 4. OPENAI CLIENT
    @Bean("openAiRestClient")
    public RestClient openAiRestClient(OpenAIProperties properties) {
        long configuredTimeout = properties.getTimeoutMs() == null ? 30000L : properties.getTimeoutMs();
        return createRestClient(properties.getBaseUrl(), configuredTimeout);
    }

    // Helper method to eliminate duplicate timeout and builder logic
    private RestClient createRestClient(String baseUrl, long configuredTimeout) {
        int timeout = Math.toIntExact(Math.max(1L, configuredTimeout));

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(timeout));
        requestFactory.setReadTimeout(Duration.ofMillis(timeout));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
