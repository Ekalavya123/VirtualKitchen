package com.processVisualisation.virtualKitchen.ai.narration;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.consumer.AiArtifactConsumer;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.narration.model.StepNarration;
import com.processVisualisation.virtualKitchen.ai.narration.storage.NarrationAudioStorage;
import com.processVisualisation.virtualKitchen.ai.narration.storage.NarrationAudioStorage.StoredAudio;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Stores generated narration audio and marks its {@link StepNarration} READY. It is called from
 * the request path straight after synthesis, and from {@code AiArtifactRecoveryJob} if that first
 * attempt failed, so the paid audio is never regenerated just because storage was down.
 * <p>
 * The artifact's {@code correlationId} is {@code narrationKey|generationToken} (see
 * {@link #correlationId}). The token is the claim the generation was started under. The commit
 * only applies while the record still carries that token, so a generation that was superseded
 * (the step was edited, or a stale lease was taken over) can never overwrite newer audio. Its file
 * is deleted instead.
 */
@Component
public class NarrationAudioArtifactConsumer implements AiArtifactConsumer {

    public static final String CONSUMER_ID = "narration-audio-upload";

    private static final String SEPARATOR = "|";
    private static final Logger log = LoggerFactory.getLogger(NarrationAudioArtifactConsumer.class);

    private final NarrationAudioStorage storage;
    private final StepNarrationStore store;
    private final AiModelRegistry modelRegistry;
    private final AiClientResolver clientResolver;
    private final NarrationProperties properties;
    private final Clock clock;

    @Autowired
    public NarrationAudioArtifactConsumer(
            NarrationAudioStorage storage,
            StepNarrationStore store,
            AiModelRegistry modelRegistry,
            AiClientResolver clientResolver,
            NarrationProperties properties) {
        this(storage, store, modelRegistry, clientResolver, properties, Clock.systemUTC());
    }

    NarrationAudioArtifactConsumer(
            NarrationAudioStorage storage,
            StepNarrationStore store,
            AiModelRegistry modelRegistry,
            AiClientResolver clientResolver,
            NarrationProperties properties,
            Clock clock) {
        this.storage = storage;
        this.store = store;
        this.modelRegistry = modelRegistry;
        this.clientResolver = clientResolver;
        this.properties = properties;
        this.clock = clock;
    }

    static String correlationId(String narrationKey, String generationToken) {
        return narrationKey + SEPARATOR + generationToken;
    }

    @Override
    public String consumerId() {
        return CONSUMER_ID;
    }

    /**
     * Recovery entry point: re-drives the commit for an artifact whose first attempt failed.
     *
     * @return the playable URL, or null when the generation had been superseded and nothing was committed
     */
    @Override
    public String consume(AiArtifact artifact, Object payload) {
        String correlationId = artifact.getCorrelationId();
        int separator = correlationId == null ? -1 : correlationId.lastIndexOf(SEPARATOR);
        if (separator <= 0) {
            throw new IllegalStateException("Malformed narration correlation id: " + correlationId);
        }
        return commit(correlationId.substring(0, separator), correlationId.substring(separator + 1),
                artifact.getProducedByModelKey(), (GeneratedAudio) payload);
    }

    /**
     * Stores {@code audio} and marks the narration READY, provided {@code token} is still the
     * narration's current claim. The request path calls this directly with its own token, because
     * a reused artifact carries the token of whichever generation first paid for the audio.
     *
     * @return the playable URL, or null when the generation had been superseded and nothing was committed
     */
    public String commit(String narrationKey, String token, String modelKey, GeneratedAudio audio) {
        // Cheap pre-check, so a superseded generation does not upload at all.
        Optional<StepNarration> current = store.find(narrationKey);
        if (current.isEmpty() || !token.equals(current.get().getGenerationToken())) {
            log.info("event=narration_generation_superseded narrationKey={} stage=before_store", narrationKey);
            return null;
        }

        StoredAudio stored = storage.store(audio.data(), audio.format(), narrationKey);
        log.info("event=narration_audio_stored narrationKey={} backend={} bytes={} audioMs={}",
                narrationKey, stored.backend(), audio.data().length, audio.durationMs());

        Optional<StepNarration> previous = store.commitReady(narrationKey, token, new StepNarrationStore.ReadyAudio(
                stored.url(),
                stored.backend(),
                stored.path(),
                audio.mimeType(),
                audio.format().name(),
                audio.durationMs(),
                audio.data().length,
                providerOf(modelKey),
                modelKey,
                audio.providerModelId(),
                audio.voice(),
                properties.getLanguageCode(),
                Instant.now(clock)));

        if (previous.isEmpty()) {
            // Superseded between the pre-check and the commit: keep the newer record, drop this file.
            log.info("event=narration_generation_superseded narrationKey={} stage=after_store", narrationKey);
            storage.delete(stored.path());
            return null;
        }
        deleteReplacedAudio(previous.get(), stored);
        return stored.url();
    }

    private void deleteReplacedAudio(StepNarration previous, StoredAudio stored) {
        if (previous.getStoragePath() == null || Objects.equals(previous.getStoragePath(), stored.path())) {
            return;
        }
        if (storage.backendName().equals(previous.getStorageBackend())) {
            storage.delete(previous.getStoragePath());
        } else {
            log.info("event=narration_audio_orphaned narrationKey={} backend={} reason=storage_backend_changed",
                    previous.getNarrationKey(), previous.getStorageBackend());
        }
    }

    private String providerOf(String modelKey) {
        if (modelKey == null) {
            return null;
        }
        return modelRegistry.find(modelKey)
                .map(model -> {
                    try {
                        return clientResolver.resolveTtsProvider(model).providerName();
                    } catch (IllegalStateException e) {
                        return model.getProviderBean();
                    }
                })
                .orElse(null);
    }
}
