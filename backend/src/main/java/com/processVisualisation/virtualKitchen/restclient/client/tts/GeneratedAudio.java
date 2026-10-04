package com.processVisualisation.virtualKitchen.restclient.client.tts;

/**
 * Audio produced by a {@link TtsProvider}, in a provider-independent shape.
 *
 * @param data the encoded audio bytes
 * @param mimeType the MIME type of {@code data} (e.g. "audio/wav")
 * @param format the container/codec of {@code data}
 * @param durationMs playback length, or null when the provider cannot tell cheaply
 * @param voice the voice that was actually used
 * @param providerModelId the provider model that produced the audio
 */
public record GeneratedAudio(
        byte[] data,
        String mimeType,
        AudioFormat format,
        Long durationMs,
        String voice,
        String providerModelId
) {
}
