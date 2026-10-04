package com.processVisualisation.virtualKitchen.ai.usage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adds up the AI spend of one background operation (a visualization job, a process generation, a narration)
 * across every AI request it makes, so the operation's "completed" log line and its persisted record can state
 * its total tokens and cost.
 * <p>
 * Requests are attributed by the {@code jobId} MDC key, which the job services set and {@code ThreadPoolTaskPool}
 * carries onto worker threads. {@code AiRequestQueueService} records each request here; the operation
 * {@link #drain drains} its totals when it ends. A request made outside any job is simply not attributed.
 * <p>
 * Static, like {@link AiUsageMeter}, so it needs no wiring through every service that might start AI work.
 */
public final class AiCostLedger {

    /** Operations that never drain (their thread died) must not leak; past this many open entries, all are cleared. */
    private static final int MAX_OPEN_OPERATIONS = 1_000;

    private static final Map<String, Totals> OPEN = new ConcurrentHashMap<>();

    /** Display-only conversion for log lines; set from {@code ai.request.usd-to-inr} at startup. */
    private static volatile double usdToInr = 88.0;

    private AiCostLedger() {
    }

    public static void configureUsdToInr(double rate) {
        usdToInr = rate;
    }

    public static double usdToInr() {
        return usdToInr;
    }

    /** Attributes one finished (or failed) AI request's usage to {@code operationId}; a null id is ignored. */
    public static void record(String operationId, AiUsageSummary usage) {
        if (operationId == null || operationId.isBlank() || usage == null) {
            return;
        }
        if (OPEN.size() >= MAX_OPEN_OPERATIONS && !OPEN.containsKey(operationId)) {
            OPEN.clear();
        }
        OPEN.merge(operationId, Totals.of(usage), Totals::plus);
    }

    /** Returns and forgets everything attributed to {@code operationId} ({@link Totals#NONE} if nothing was). */
    public static Totals drain(String operationId) {
        if (operationId == null) {
            return Totals.NONE;
        }
        Totals totals = OPEN.remove(operationId);
        return totals == null ? Totals.NONE : totals;
    }

    /**
     * @param requests          AI requests made (each may include retries)
     * @param inputTokens       all input tokens, cached included
     * @param outputTokens      output tokens, reasoning tokens included
     * @param costUsd           summed estimated cost of the requests that could be priced
     * @param unpricedRequests  requests whose model has no configured prices (their cost is unknown, not zero)
     */
    public record Totals(int requests, long inputTokens, long cachedTokens, long outputTokens, double costUsd,
                         int unpricedRequests) {

        public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0);

        static Totals of(AiUsageSummary usage) {
            return new Totals(1, usage.promptTokens(), usage.cachedTokens(),
                    usage.completionTokens() + usage.thoughtsTokens(),
                    usage.estimatedCostUsd() == null ? 0 : usage.estimatedCostUsd(),
                    usage.estimatedCostUsd() == null ? 1 : 0);
        }

        Totals plus(Totals other) {
            return new Totals(requests + other.requests, inputTokens + other.inputTokens,
                    cachedTokens + other.cachedTokens, outputTokens + other.outputTokens,
                    costUsd + other.costUsd, unpricedRequests + other.unpricedRequests);
        }

        /**
         * {@code aiRequests=.. inputTokens=.. outputTokens=.. costUsd=.. costInr=..}, for an operation's log line.
         * Cost is shown as a lower bound ({@code >=}) when some requests could not be priced.
         */
        public String toLogFields() {
            String bound = unpricedRequests > 0 ? ">=" : "";
            return "aiRequests=" + requests
                    + " inputTokens=" + inputTokens
                    + " outputTokens=" + outputTokens
                    + " costUsd=" + bound + String.format("%.6f", costUsd)
                    + " costInr=" + bound + String.format("%.4f", costUsd * usdToInr);
        }
    }
}
