package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a provider {@link ImageGenerationClient}: counts each call's token usage against the running AI request
 * (so image cost is priced and persisted like text cost), and writes one DEBUG line per provider call. The
 * operator-facing summary — model, tokens, cost, duration — is {@code ai_request_completed}, written once per
 * request by {@code AiRequestQueueService}. The prompt is never logged.
 */
class LoggingImageGenerationClient implements ImageGenerationClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingImageGenerationClient.class);

    private final ImageGenerationClient delegate;
    private final String provider;
    private final String model;

    LoggingImageGenerationClient(ImageGenerationClient delegate, String provider, String model) {
        this.delegate = delegate;
        this.provider = provider;
        this.model = model;
    }

    @Override
    public GeneratedImage generate(String prompt) throws JsonProcessingException {
        long startedAt = System.nanoTime();
        try {
            GeneratedImage image = delegate.generate(prompt);
            AiUsageMeter.record(image == null ? null : image.usage());
            log.debug("event=image_generation_completed provider={} model={} durationMs={} promptChars={} bytes={} mimeType={}",
                    provider, model, LoggingAIClient.elapsedMs(startedAt), prompt == null ? 0 : prompt.length(),
                    image == null || image.data() == null ? 0 : image.data().length,
                    image == null ? null : image.mimeType());
            return image;
        } catch (JsonProcessingException | RuntimeException ex) {
            log.debug("event=image_generation_failed provider={} model={} durationMs={} errorType={} status={}",
                    provider, model, LoggingAIClient.elapsedMs(startedAt), ex.getClass().getSimpleName(),
                    LoggingAIClient.httpStatusOf(ex));
            throw ex;
        }
    }
}
