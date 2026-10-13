package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** {@link TextOutputSink} that keeps saved outputs in memory. */
class RecordingTextSink implements TextOutputSink {

    record Saved(String id, String requestId, String attemptId, OperationContext context, String output, String input) {
    }

    final List<Saved> saved = new CopyOnWriteArrayList<>();

    @Override
    public String save(String analyticsRequestId, String attemptId, OperationContext context, AiCallRecord call) {
        String id = "resp-" + (saved.size() + 1);
        saved.add(new Saved(id, analyticsRequestId, attemptId, context, call.getOutputText(), call.getInputText()));
        return id;
    }
}
