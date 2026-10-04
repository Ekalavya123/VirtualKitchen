package com.processVisualisation.virtualKitchen.common.logging;

import com.processVisualisation.virtualKitchen.ai.usage.AiCostLedger;
import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * The start/end pair every background operation writes to {@code application.log}, so its whole lifecycle reads
 * as two lines:
 * <pre>
 *   event=&lt;name&gt;_started   &lt;what it is about&gt;
 *   event=&lt;name&gt;_completed durationMs=.. &lt;outcome&gt; aiRequests=.. inputTokens=.. outputTokens=.. costUsd=.. costInr=..
 *   event=&lt;name&gt;_failed    durationMs=.. &lt;outcome&gt; aiRequests=.. ... errorType=.. error="reason" (+ stack trace for bugs)
 * </pre>
 * The AI totals are everything {@link AiCostLedger} attributed to the operation's {@code jobId} while it ran.
 * Intermediate steps belong at DEBUG, which goes to {@code debug.log} only.
 * <p>
 * Never put user content (prompts, recipe text) or secrets in the detail strings: ids, counts and sizes only.
 */
public final class OperationLog {

    private final Logger log;
    private final String name;
    private final String operationId;
    private final long startedAt = System.nanoTime();
    private AiCostLedger.Totals aiTotals;

    private OperationLog(Logger log, String name, String operationId) {
        this.log = log;
        this.name = name;
        this.operationId = operationId;
    }

    /**
     * Logs {@code event=<name>_started} and binds {@code operationId} as this thread's {@code jobId} MDC key (worker
     * threads inherit it through {@code ThreadPoolTaskPool}), so every line and AI request of the operation carries it.
     *
     * @param details {@code key=value} context such as ids and sizes, or empty
     */
    public static OperationLog start(Logger log, String name, String operationId, String details) {
        if (operationId != null) {
            MDC.put(MdcKeys.JOB_ID, operationId);
        }
        log.info("event={}_started {}", name, details == null ? "" : details);
        return new OperationLog(log, name, operationId);
    }

    /** Logs {@code event=<name>_completed} at INFO with duration, {@code details} and the operation's AI totals. */
    public void completed(String details) {
        log.info("event={}_completed durationMs={} {}{}", name, elapsedMs(), prefix(details), aiTotals().toLogFields());
    }

    /** Like {@link #completed} but at WARN, for an operation that finished with some parts failed. */
    public void completedWithErrors(String details) {
        log.warn("event={}_completed durationMs={} {}{}", name, elapsedMs(), prefix(details), aiTotals().toLogFields());
    }

    /**
     * Logs {@code event=<name>_failed} with the reason, at the level {@link FailureLogger} picks for the error: WARN
     * for business outcomes, ERROR for known external failures, ERROR with the stack trace for anything else.
     */
    public void failed(Throwable error, String details) {
        FailureLogger.logFailure(log, name + "_failed",
                error, "durationMs=" + elapsedMs() + " " + prefix(details) + aiTotals().toLogFields());
    }

    /** The AI spend attributed to this operation so far, drained once; safe to call before persisting it. */
    public AiCostLedger.Totals aiTotals() {
        if (aiTotals == null) {
            aiTotals = AiCostLedger.drain(operationId);
        }
        return aiTotals;
    }

    public long elapsedMs() {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String prefix(String details) {
        return details == null || details.isBlank() ? "" : details.trim() + " ";
    }
}
