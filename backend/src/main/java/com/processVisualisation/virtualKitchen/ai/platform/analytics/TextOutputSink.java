package com.processVisualisation.virtualKitchen.ai.platform.analytics;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.platform.usage.AiCallRecord;

/** Retains a text call's generated output in the existing {@code ai_responses} store, linked to its analytics attempt. */
public interface TextOutputSink {

    /** @return the id of the stored output document */
    String save(String analyticsRequestId, String attemptId, OperationContext context, AiCallRecord call);
}
