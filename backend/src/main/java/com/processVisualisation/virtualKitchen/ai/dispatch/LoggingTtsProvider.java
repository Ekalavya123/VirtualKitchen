package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Text-to-speech counterpart of {@link LoggingImageGenerationClient}: one {@code tts_synthesis_completed}
 * (INFO) or {@code tts_synthesis_failed} (WARN, no stack) line per provider call. The narrated text and
 * the audio bytes are never logged, only their sizes.
 */
class LoggingTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingTtsProvider.class);

    private final TtsProvider delegate;
    private final String modelKey;

    LoggingTtsProvider(TtsProvider delegate, String modelKey) {
        this.delegate = delegate;
        this.modelKey = modelKey;
    }

    @Override
    public String providerName() {
        return delegate.providerName();
    }

    @Override
    public GeneratedAudio synthesize(TtsRequest request) {
        int chars = request.text() == null ? 0 : request.text().length();
        log.debug("event=tts_synthesis_started provider={} modelKey={} model={} chars={}",
                delegate.providerName(), modelKey, request.providerModelId(), chars);
        long startedAt = System.nanoTime();
        try {
            GeneratedAudio audio = delegate.synthesize(request);
            log.info("event=tts_synthesis_completed provider={} modelKey={} model={} voice={} durationMs={} chars={} "
                            + "bytes={} audioMs={} mimeType={}",
                    delegate.providerName(), modelKey, audio.providerModelId(), audio.voice(),
                    LoggingAIClient.elapsedMs(startedAt), chars, audio.data() == null ? 0 : audio.data().length,
                    audio.durationMs(), audio.mimeType());
            return audio;
        } catch (RuntimeException ex) {
            log.warn("event=tts_synthesis_failed provider={} modelKey={} model={} durationMs={} chars={} errorType={} status={}",
                    delegate.providerName(), modelKey, request.providerModelId(), LoggingAIClient.elapsedMs(startedAt),
                    chars, ex.getClass().getSimpleName(), LoggingAIClient.httpStatusOf(ex));
            throw ex;
        }
    }
}
