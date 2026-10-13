package com.processVisualisation.virtualKitchen.ai.platform.usage;

import java.util.List;

/**
 * Receives what happened on each provider call made for one AI request (see {@link AiUsageMeter#recordCall}).
 * Implemented by the analytics recorder; attached to the request's {@link AiUsage}, so it follows the work onto
 * whichever pool thread runs it, and still receives a call that completes after the request timed out.
 */
public interface AiCallListener {

    void onCall(AiCallRecord record);

    /** The latest call's output passed (or failed) the caller's own validation. */
    default void onValidation(boolean passed, List<String> errors) {
    }

    /** Id of the {@code ai_responses} document holding the latest call's text output, or null. */
    default String lastTextOutputId() {
        return null;
    }
}
