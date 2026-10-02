package com.processVisualisation.virtualKitchen.ai.usage;

import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

/**
 * Accumulates the token usage of every provider call made for one AI request job. Thread-safe: a job's work may
 * run on a pool thread while the queue reads the totals, and a timed-out attempt can still report late.
 */
public class AiUsage {

    private static final double PER_MILLION = 1_000_000d;

    private int providerCalls;
    private long promptTokens;
    private long cachedTokens;
    private long completionTokens;
    private long thoughtsTokens;
    private long totalTokens;

    public synchronized void add(AIResponse response) {
        if (response == null) return;
        providerCalls++;
        promptTokens += orZero(response.getPromptTokens());
        cachedTokens += orZero(response.getCachedTokens());
        completionTokens += orZero(response.getCompletionTokens());
        thoughtsTokens += orZero(response.getThoughtsTokens());
        totalTokens += orZero(response.getTotalTokens());
    }

    /** Totals so far, priced with {@code model}'s configured list prices (cost null when it has none). */
    public synchronized AiUsageSummary summarize(ModelDefinition model) {
        return new AiUsageSummary(providerCalls, promptTokens, cachedTokens, completionTokens, thoughtsTokens,
                totalTokens, estimateCostUsd(model));
    }

    private Double estimateCostUsd(ModelDefinition model) {
        if (model == null || model.getInputUsdPerMillion() == null || model.getOutputUsdPerMillion() == null) {
            return null;
        }
        double cachedRate = model.getCachedInputUsdPerMillion() != null ? model.getCachedInputUsdPerMillion() : model.getInputUsdPerMillion();
        long uncachedPrompt = Math.max(0, promptTokens - cachedTokens);
        return (uncachedPrompt * model.getInputUsdPerMillion()
                + cachedTokens * cachedRate
                + (completionTokens + thoughtsTokens) * model.getOutputUsdPerMillion()) / PER_MILLION;
    }

    private static long orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
