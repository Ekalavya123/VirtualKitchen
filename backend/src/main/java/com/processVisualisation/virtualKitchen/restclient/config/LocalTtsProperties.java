package com.processVisualisation.virtualKitchen.restclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code ai.local-tts.*}: a self-hosted speech engine reached over HTTP through the
 * OpenAI-compatible {@code POST /v1/audio/speech} contract (Kokoro-FastAPI, Speaches, LocalAI, ...).
 * Swapping the engine is a configuration change only; nothing here names a specific model.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.local-tts")
public class LocalTtsProperties {

    private String baseUrl = "http://localhost:8880";
    private String speechEndpoint = "/v1/audio/speech";
    /** Fallback model id when the registry entry does not name one. */
    private String model = "kokoro";
    private String voice = "af_heart";
    /** Requested response format: wav, mp3 or opus. */
    private String responseFormat = "wav";
    private Long timeoutMs = 60000L;
    /** Optional bearer token, for engines that sit behind authentication. */
    private String apiKey;
}
