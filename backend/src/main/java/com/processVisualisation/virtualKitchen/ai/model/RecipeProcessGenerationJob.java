package com.processVisualisation.virtualKitchen.ai.model;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
import com.processVisualisation.virtualKitchen.ai.usage.AiCostLedger;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the progress of an async AI Process generation run started via
 * {@code RecipeProcessGenerationJobService.startJob}. Its lifecycle is
 * {@link RecipeProcessGenerationJobStatus} (QUEUED/IN_PROGRESS/COMPLETED/FAILED).
 */
@Data
@Document(collection = "process_generation_job")
public class RecipeProcessGenerationJob {

    @Id
    private String id;

    @Indexed
    private Long userId;

    @Indexed
    private Long recipeId;

    @Indexed
    private String clientRequestId;

    private RecipeProcessGenerationJobStatus status;

    private RecipeProcessGenerationStage stage;

    private RecipeProcessGenerationResultDTO result;

    private String errorMessage;

    /**
     * Set to {@code gen:<userId>:<recipeId>} only while the job is QUEUED/IN_PROGRESS and unset once
     * it terminates. The unique sparse index makes "one running generation per user+recipe" atomic:
     * a concurrent second start fails the insert and joins the job that won instead.
     */
    @Indexed(unique = true, sparse = true)
    private String activeKey;

    /** When the frontend loaded {@link #result} into the user's working session; null until then. */
    private Instant resultAppliedAt;

    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;

    /** Total AI requests, tokens and estimated cost of this job, recorded when it finishes. */
    private AiCostLedger.Totals aiUsage;
}
