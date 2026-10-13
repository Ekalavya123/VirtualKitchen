package com.processVisualisation.virtualKitchen.ai.provider;

import java.time.Instant;

/**
 * Receives the provider calls a client makes on its own initiative while serving a request (e.g. creating a
 * Gemini prompt cache), so they are metered and attributed to the request that triggered them like the main
 * call. The AI platform's usage meter implements it; clients never depend on the platform themselves.
 */
public interface ProviderCallReporter {

    /** Reports nothing; for clients built outside Spring (tests). */
    ProviderCallReporter NONE = call -> {
    };

    /**
     * One side call to a provider.
     *
     * @param purpose     what the call was for, e.g. {@code PROMPT_CACHE_CREATE}
     * @param totalTokens tokens the provider reported for the call, or null
     * @param errorType   the failure's exception type, or null on success
     */
    record ProviderCall(String purpose, String provider, String providerModelId, Instant startedAt, Instant endedAt,
                        long durationMs, boolean success, Integer inputChars, Long totalTokens, String errorType,
                        String errorMessage) {
    }

    /** Records {@code call}; must never throw into the client. */
    void report(ProviderCall call);
}
