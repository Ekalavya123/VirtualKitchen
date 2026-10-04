package com.processVisualisation.virtualKitchen.restclient.client;

/**
 * Token counts a provider reported for one call, in a provider-independent shape. Any count the
 * provider did not report is null. Prices are not the provider's concern: cost is computed from
 * these counts and the model registry's configured rates.
 *
 * @param inputTokens tokens billed as input (text, and images for image models)
 * @param outputTokens tokens billed as output (generated text, image or audio tokens)
 * @param thoughtsTokens reasoning tokens, billed as output but reported separately
 * @param totalTokens the provider's own total
 */
public record ProviderUsage(Long inputTokens, Long outputTokens, Long thoughtsTokens, Long totalTokens) {

    /** For providers that report nothing (local engines); still counts as one provider call. */
    public static final ProviderUsage NONE = new ProviderUsage(null, null, null, null);
}
