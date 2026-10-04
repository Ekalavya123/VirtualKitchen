package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of AI-driven Process generation: a semantic MAIN process plus zero
 * or more SUBPROCESSes (see {@link GeneratedRecipeProcessDTO}), not yet persisted
 * — the frontend loads this straight into the current Recipe working
 * session (RecipeSessionContext) for the user to review/edit, and nothing is
 * written to the database until they explicitly Save.
 * <p>
 * An EDIT job carries its operations in {@link #edit} instead, with mainProcess/subprocesses left null.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeProcessGenerationResultDTO {

    private GeneratedRecipeProcessDTO mainProcess;
    private List<GeneratedRecipeProcessDTO> subprocesses;

    /** Which kind of request produced this result; null on results stored before modes existed (= CREATE). */
    private ProcessGenerationMode mode;
    private RecipeProcessEditResultDTO edit;

    private String modelUsed;
    private String modelTier;
    private boolean usedFallback;
    private String fallbackReason;
}
