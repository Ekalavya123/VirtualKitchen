package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;

/** The result of a completed {@link AiRequestQueueService} execution: the job id, the work's return value, and which model actually served it. */
public record AiRequestOutcome<R>(String jobId, R value, ModelSelectionOutcome selection) {
}
