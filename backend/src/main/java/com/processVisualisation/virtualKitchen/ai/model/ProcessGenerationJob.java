package com.processVisualisation.virtualKitchen.ai.model;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationResultDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the progress of an async AI Process generation run started via
 * {@code ProcessGenerationJobService.startJob}, mirroring
 * {@link RecipeFlowGenerationJob}. Reuses {@link RecipeFlowGenerationJobStatus}
 * (QUEUED/IN_PROGRESS/COMPLETED/FAILED) directly — that lifecycle is generic
 * to any async generation job, not specific to the legacy flow shape.
 */
@Data
@Document(collection = "process_generation_job")
public class ProcessGenerationJob {

    @Id
    private String id;

    @Indexed
    private Long userId;

    @Indexed
    private Long recipeId;

    @Indexed
    private String clientRequestId;

    private RecipeFlowGenerationJobStatus status;

    private ProcessGenerationStage stage;

    private ProcessGenerationResultDTO result;

    private String errorMessage;

    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;
}
