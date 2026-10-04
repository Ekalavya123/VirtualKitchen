package com.processVisualisation.virtualKitchen.restclient.client.tts;

/**
 * Provider-independent synthesis request.
 * <p>
 * Speaking rate is deliberately absent: audio is always generated at natural speed and the player
 * applies playback speed client-side, so a speed change never costs a regeneration.
 *
 * @param text the text to speak
 * @param providerModelId the provider's model id, from the selected {@code ModelDefinition}
 * @param voice the voice to use, or null for the provider's configured default
 * @param languageCode BCP-47 language code (e.g. "en-US"), or null to let the provider decide
 * @param preferredFormat the format the caller would like; a provider may return another and says so
 */
public record TtsRequest(
        String text,
        String providerModelId,
        String voice,
        String languageCode,
        AudioFormat preferredFormat
) {
}
