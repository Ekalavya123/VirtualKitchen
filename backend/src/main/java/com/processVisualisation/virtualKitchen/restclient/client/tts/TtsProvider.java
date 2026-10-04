package com.processVisualisation.virtualKitchen.restclient.client.tts;

/**
 * Common contract implemented by every text-to-speech provider (Gemini, a local OpenAI-compatible
 * engine, ...). A provider only turns text into audio bytes: where the bytes are stored, how they
 * are cached and when they go stale are all decided above it, so nothing provider-specific ever
 * leaks out of the implementation.
 * <p>
 * Implementations are registered under a stable bean name (e.g. {@code geminiTtsProvider}) and
 * selected through the AI model registry ({@code ai.models[n].provider-bean} +
 * {@code ai.default-model.TEXT_TO_SPEECH}), exactly like the text and image clients.
 * <p>
 * Failures must surface as the typed {@code AIClientException} hierarchy: {@code
 * AiRequestQueueService} only retries {@code AITimeoutException}/{@code AICommunicationException}.
 */
public interface TtsProvider {

    /** Short, stable provider label recorded with the generated audio (e.g. "gemini", "local"). */
    String providerName();

    /**
     * Synthesizes speech for the request's text.
     *
     * @return the audio bytes and their metadata, never null
     */
    GeneratedAudio synthesize(TtsRequest request);
}
