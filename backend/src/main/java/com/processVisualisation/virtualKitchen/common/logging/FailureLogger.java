package com.processVisualisation.virtualKitchen.common.logging;

import com.processVisualisation.virtualKitchen.ai.queue.AiQueueFullException;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestTimeoutException;
import com.processVisualisation.virtualKitchen.ai.routing.NoAvailableModelException;
import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIClientException;
import org.slf4j.Logger;

/**
 * Logs a failure once, at the boundary that finally handles it (an async job's catch block), with a severity
 * that matches what kind of failure it is:
 * <ul>
 *   <li>business outcomes (the AI produced an invalid process) — WARN, no stack;</li>
 *   <li>known external/AI failures (provider error, timeout, no model, queue full) — ERROR, no stack, since the
 *       exception type and message already say what happened;</li>
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
            log.warn("{} errorType={} error={}", prefix, errorType, error.getMessage());
        } else if (isKnownExternalFailure(error)) {
            log.error("{} errorType={} error={}", prefix, errorType, error.getMessage());
        } else {
            log.error("{} errorType={}", prefix, errorType, error);
        }
    }

    static boolean isBusinessFailure(Throwable error) {
        return error instanceof RecipeProcessAiException || error instanceof ProcessValidationException;
    }

    static boolean isKnownExternalFailure(Throwable error) {
        return error instanceof AIClientException
                || error instanceof AiRequestTimeoutException
                || error instanceof NoAvailableModelException
                || error instanceof AiQueueFullException;
    }
}
