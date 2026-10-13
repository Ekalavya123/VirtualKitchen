package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;

/** Retains a text call's generated output in the existing {@code ai_responses} store, linked to its analytics attempt. */
public interface TextOutputSink {

    /** @return the id of the stored output document */
    String save(String analyticsRequestId, String attemptId, OperationContext context, AiCallRecord call);
}
