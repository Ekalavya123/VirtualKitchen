package com.processVisualisation.virtualKitchen.ai.platform.dispatch;

import com.processVisualisation.virtualKitchen.ai.platform.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.ai.provider.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.ai.provider.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.ai.provider.tts.TtsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Text-to-speech counterpart of {@link LoggingImageGenerationClient}: counts each call's token usage against the
 * running AI request and writes one DEBUG line per provider call. The narrated text and the audio bytes are never
 * logged, only their sizes.
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
        long startedAt = System.nanoTime();
        Instant startedAtInstant = Instant.now();
        try {
            GeneratedAudio audio = delegate.synthesize(request);
            AiUsageMeter.record(audio == null ? null : audio.usage());
            AiUsageMeter.recordCall(AiCallRecords.withUsage(
                            AiCallRecords.started("SPEECH_SYNTHESIS", delegate.providerName(), request.providerModelId(),
                                    startedAtInstant, startedAt),
                            audio == null ? null : audio.usage())
                    .success(true)
                    .reportedModel(audio == null ? null : audio.providerModelId())
                    .inputChars(chars)
                    .audioDurationMs(audio == null ? null : audio.durationMs())
                    .outputBytes(audio == null || audio.data() == null ? null : (long) audio.data().length)
                    .build());
            log.debug("event=tts_synthesis_completed provider={} modelKey={} model={} voice={} durationMs={} chars={} "
                            + "bytes={} audioMs={} mimeType={}",
                    delegate.providerName(), modelKey, audio.providerModelId(), audio.voice(),
                    LoggingAiClient.elapsedMs(startedAt), chars, audio.data() == null ? 0 : audio.data().length,
                    audio.durationMs(), audio.mimeType());
            return audio;
        } catch (RuntimeException ex) {
            AiUsageMeter.recordCall(AiCallRecords.failed(
                    AiCallRecords.started("SPEECH_SYNTHESIS", delegate.providerName(), request.providerModelId(),
                            startedAtInstant, startedAt).inputChars(chars), ex));
            log.debug("event=tts_synthesis_failed provider={} modelKey={} model={} durationMs={} chars={} errorType={} status={}",
                    delegate.providerName(), modelKey, request.providerModelId(), LoggingAiClient.elapsedMs(startedAt),
                    chars, ex.getClass().getSimpleName(), LoggingAiClient.httpStatusOf(ex));
            throw ex;
        }
    }
}
