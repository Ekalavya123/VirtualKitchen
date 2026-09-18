package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of AI-driven Process generation: a semantic MAIN process plus zero
 * or more SUBPROCESSes (see {@link GeneratedProcessDTO}), not yet persisted
 * — the frontend loads this straight into the current Recipe working
 * session (RecipeSessionContext) for the user to review/edit, and nothing is
 * written to the database until they explicitly Save.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessGenerationResultDTO {

    private GeneratedProcessDTO mainProcess;
    private List<GeneratedProcessDTO> subprocesses;

    private String modelUsed;
    private String modelTier;
    private boolean usedFallback;
    private String fallbackReason;
}
