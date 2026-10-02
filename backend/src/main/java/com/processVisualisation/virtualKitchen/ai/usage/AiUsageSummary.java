package com.processVisualisation.virtualKitchen.ai.usage;

/**
 * Token usage and estimated price of one AI request job, summed over every provider call it made (the first
 * call, validation retries, transient-failure retries). Stored on the job and on its credit ledger entries.
 *
 * @param providerCalls     number of provider responses counted
 * @param promptTokens      all input tokens, including {@code cachedTokens}
 * @param cachedTokens      input tokens served from the provider's context cache (billed at the cached rate)
 * @param completionTokens  visible output tokens
 * @param thoughtsTokens    reasoning tokens, billed as output but not included in {@code completionTokens}
 * @param totalTokens       as reported by the provider
 * @param estimatedCostUsd  tokens x the model's configured list prices; null when the model has no prices
 *                          configured. Excludes context-cache storage, which is billed per hour, not per request.
 */
public record AiUsageSummary(
        int providerCalls,
        long promptTokens,
        long cachedTokens,
        long completionTokens,
        long thoughtsTokens,
        long totalTokens,
        Double estimatedCostUsd
) {
}
