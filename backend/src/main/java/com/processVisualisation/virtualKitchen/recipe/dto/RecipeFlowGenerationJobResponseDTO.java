package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload reporting the progress and result of an asynchronous,
 * AI-driven recipe-flow generation job. {@code progressPercent} reflects the
 * job's current {@code stage} (queued/building prompt/calling the model/
 * validating/retrying/persisting/done) rather than a step count, since a
 * single generation run has no independently-completable sub-steps.
 */
@Data
@Builder
public class RecipeFlowGenerationJobResponseDTO {
    private String jobId;
    private String status;
    private String stage;
    private int progressPercent;
    private RecipeFlowGenerationResponseDTO result;
    private String errorMessage;
}
