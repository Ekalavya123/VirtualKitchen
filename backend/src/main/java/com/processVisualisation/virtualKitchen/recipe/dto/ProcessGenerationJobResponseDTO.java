package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload reporting the progress and result of an asynchronous
 * AI-driven Process generation job. {@code progressPercent} reflects the
 * job's current {@code stage} (queued/building prompt/calling the model/
 * validating/retrying/done), mirroring {@code RecipeFlowGenerationJobResponseDTO}.
 */
@Data
@Builder
public class ProcessGenerationJobResponseDTO {
    private String jobId;
    private String status;
    private String stage;
    private int progressPercent;
    private ProcessGenerationResultDTO result;
    private String errorMessage;
}
