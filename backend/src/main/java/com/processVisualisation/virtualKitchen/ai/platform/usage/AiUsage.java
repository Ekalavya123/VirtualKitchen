package com.processVisualisation.virtualKitchen.ai.platform.usage;

import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.provider.ProviderUsage;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;

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
    /** Receives a per-call record of every provider call made for this job (analytics); null when none is attached. */
    private volatile AiCallListener callListener;

    public void setCallListener(AiCallListener callListener) {
        this.callListener = callListener;
    }

    public AiCallListener getCallListener() {
        return callListener;
    }

    public synchronized void add(AiResponse response) {
        if (response == null) return;
        providerCalls++;
        promptTokens += orZero(response.getPromptTokens());
        cachedTokens += orZero(response.getCachedTokens());
        completionTokens += orZero(response.getCompletionTokens());
        thoughtsTokens += orZero(response.getThoughtsTokens());
        totalTokens += orZero(response.getTotalTokens());
    }

    /** Counts one image/speech provider call; a provider that reports no tokens still counts as a call. */
    public synchronized void add(ProviderUsage usage) {
        providerCalls++;
        if (usage == null) return;
        promptTokens += orZero(usage.inputTokens());
        cachedTokens += orZero(usage.cachedTokens());
        completionTokens += orZero(usage.outputTokens());
        thoughtsTokens += orZero(usage.thoughtsTokens());
        totalTokens += orZero(usage.totalTokens());
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

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
