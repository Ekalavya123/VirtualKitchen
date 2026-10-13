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
 * @param cachedTokens input tokens served from the provider's context cache (included in {@code inputTokens})
 * @param reportedModel the model version the provider says served the call, when it reports one
 * @param imageCount images produced by the call (image models only)
 * @param imageWidth width of the produced image in pixels, when known
 * @param imageHeight height of the produced image in pixels, when known
 */
public record ProviderUsage(Long inputTokens, Long outputTokens, Long thoughtsTokens, Long totalTokens,
                            Long cachedTokens, String reportedModel,
                            Integer imageCount, Integer imageWidth, Integer imageHeight) {

    /** For providers that report nothing (local engines); still counts as one provider call. */
    public static final ProviderUsage NONE = new ProviderUsage(null, null, null, null);

    public ProviderUsage(Long inputTokens, Long outputTokens, Long thoughtsTokens, Long totalTokens) {
        this(inputTokens, outputTokens, thoughtsTokens, totalTokens, null, null, null, null, null);
    }

    /** True when the provider reported at least one token count. */
    public boolean hasTokenCounts() {
        return inputTokens != null || outputTokens != null || thoughtsTokens != null || totalTokens != null;
    }

    /** A copy describing {@code count} produced images of the given size (either dimension may be null). */
    public ProviderUsage withImage(int count, Integer width, Integer height) {
        return new ProviderUsage(inputTokens, outputTokens, thoughtsTokens, totalTokens, cachedTokens, reportedModel,
                count, width, height);
    }
}
