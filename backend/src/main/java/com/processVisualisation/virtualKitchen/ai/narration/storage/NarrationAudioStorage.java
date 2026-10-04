package com.processVisualisation.virtualKitchen.ai.narration.storage;

import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;

/**
 * Where generated narration audio lives. Exactly one implementation is active, chosen by
 * {@code ai.narration.storage.type}; adding object storage (S3, GCS, ...) means adding one more.
 * <p>
 * Every stored object gets a fresh, unguessable name, so a URL never changes meaning and can be
 * cached forever by the browser: a regenerated narration is a new object, not an overwrite.
 */
public interface NarrationAudioStorage {

    /** Stable name of this backend, persisted with each narration so deletes go to the right place. */
    String backendName();

    /**
     * Persists audio and returns where it can be played from.
     *
     * @param narrationKey the owning narration's key, usable as a path namespace
     */
    StoredAudio store(byte[] data, AudioFormat format, String narrationKey);

    /** Deletes a previously stored object; must not throw for an object that is already gone. */
    void delete(String storagePath);

    /**
     * @param url the URL the browser plays the audio from
     * @param backend {@link #backendName()} of the storage that holds it
     * @param path the backend-specific location, for {@link #delete}
     */
    record StoredAudio(String url, String backend, String path) {
    }
}
