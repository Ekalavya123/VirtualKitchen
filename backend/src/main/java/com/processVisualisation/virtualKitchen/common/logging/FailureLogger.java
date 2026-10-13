package com.processVisualisation.virtualKitchen.common.logging;

import com.processVisualisation.virtualKitchen.common.web.ApiException;
import org.slf4j.Logger;

/**
 * Logs a failure once, at the boundary that finally handles it (an async job's catch block), with a severity
 * that matches what kind of failure it is, read from the {@link ApiException.Outcome} the exception carries:
 * <ul>
 *   <li>business outcomes and rejections (the AI produced an invalid process, a conflict, a full queue) — WARN,
 *       no stack;</li>
 *   <li>known external/AI failures (provider error, timeout, no model) — ERROR, no stack, since the exception
 *       type and message already say what happened;</li>
 *   <li>anything else is a bug — ERROR with the full stack trace.</li>
 * </ul>
 */
public final class FailureLogger {

    private FailureLogger() {
    }

    public static void logFailure(Logger log, String event, Throwable error) {
        logFailure(log, event, error, null);
    }

    /**
     * @param details extra {@code key=value} context for this failure (e.g. {@code visualizationKey=...}), or null.
     *                Never put user content or secrets here.
     */
    public static void logFailure(Logger log, String event, Throwable error, String details) {
        String prefix = details == null || details.isEmpty() ? "event=" + event : "event=" + event + " " + details;
        String errorType = error.getClass().getSimpleName();
        if (isBusinessFailure(error)) {
            log.warn("{} errorType={} error=\"{}\"", prefix, errorType, oneLine(error.getMessage()));
        } else if (isKnownExternalFailure(error)) {
            log.error("{} errorType={} error=\"{}\"", prefix, errorType, oneLine(error.getMessage()));
        } else {
            log.error("{} errorType={}", prefix, errorType, error);
        }
    }

    private static final int MAX_MESSAGE_CHARS = 300;

    /**
     * An exception message made safe for a single {@code key="value"} log field: newlines and quotes flattened,
     * long provider error bodies truncated.
     */
    public static String oneLine(String message) {
        if (message == null) {
            return "";
        }
        String flat = message.replaceAll("\\s+", " ").replace('"', '\'').trim();
        return flat.length() <= MAX_MESSAGE_CHARS ? flat : flat.substring(0, MAX_MESSAGE_CHARS) + "...";
    }

    static boolean isBusinessFailure(Throwable error) {
        return error instanceof ApiException api && api.getOutcome() != ApiException.Outcome.UPSTREAM_FAILURE;
    }

    static boolean isKnownExternalFailure(Throwable error) {
        return error instanceof ApiException api && api.getOutcome() == ApiException.Outcome.UPSTREAM_FAILURE;
    }
}
