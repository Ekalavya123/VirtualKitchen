package com.processVisualisation.virtualKitchen.ai.narration.storage;

import com.processVisualisation.virtualKitchen.ai.narration.NarrationProperties;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.config.SupabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/**
 * Production storage: narration audio in the same Supabase bucket and credentials as step images,
 * under {@code ai.narration.storage.supabase.path-prefix}. Uploads reuse {@link ImageStorageClient},
 * whose {@code upload(bytes, mimeType, path)} is media-agnostic; the bucket must allow {@code audio/*}.
 */
@Component
@ConditionalOnProperty(name = "ai.narration.storage.type", havingValue = "supabase")
public class SupabaseNarrationAudioStorage implements NarrationAudioStorage {

    public static final String BACKEND = "supabase";

    private static final Logger log = LoggerFactory.getLogger(SupabaseNarrationAudioStorage.class);

    private final ImageStorageClient objectStorage;
    private final RestClient restClient;
    private final SupabaseProperties supabaseProperties;
    private final String pathPrefix;

    public SupabaseNarrationAudioStorage(
            ImageStorageClient objectStorage,
            @Qualifier("supabaseRestClient") RestClient restClient,
            SupabaseProperties supabaseProperties,
            NarrationProperties narrationProperties) {
        this.objectStorage = objectStorage;
        this.restClient = restClient;
        this.supabaseProperties = supabaseProperties;
        this.pathPrefix = narrationProperties.getStorage().getSupabase().getPathPrefix().replaceAll("^/+|/+$", "");
    }

    @Override
    public String backendName() {
        return BACKEND;
    }

    @Override
    public StoredAudio store(byte[] data, AudioFormat format, String narrationKey) {
        String path = pathPrefix + "/" + narrationKey.replace("::", "/") + "/" + UUID.randomUUID() + "." + format.extension();
        String url = objectStorage.upload(data, format.mimeType(), path);
        return new StoredAudio(url, BACKEND, path);
    }

    @Override
    public void delete(String storagePath) {
        try {
            restClient.delete()
                    .uri("/storage/v1/object/{bucket}/{path}", supabaseProperties.getBucket(), storagePath)
                    .header("Authorization", "Bearer " + supabaseProperties.getServiceKey())
                    .header("apikey", supabaseProperties.getServiceKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Best effort: an orphaned object costs storage, never correctness.
            log.warn("event=narration_object_delete_failed path={} errorType={}", storagePath, e.getClass().getSimpleName());
        }
    }
}
