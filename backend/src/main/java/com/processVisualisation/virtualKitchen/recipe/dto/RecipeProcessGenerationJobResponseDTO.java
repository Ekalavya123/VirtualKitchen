package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload reporting the progress and result of an asynchronous
 * AI-driven Process generation job. {@code progressPercent} reflects the
 * job's current {@code stage} (queued/building prompt/calling the model/
 * validating/retrying/done).
 */
@Data
@Builder
public class RecipeProcessGenerationJobResponseDTO {
    private String jobId;
    private String status;
    private String stage;
    /** "CREATE" or "EDIT": which result shape {@code result} carries once the job completes. */
    private String mode;
    private int progressPercent;
    private RecipeProcessGenerationResultDTO result;
    private String errorMessage;
    /** True when the start request joined a generation already running for this recipe instead of starting a new one. */
    private boolean reused;
}
