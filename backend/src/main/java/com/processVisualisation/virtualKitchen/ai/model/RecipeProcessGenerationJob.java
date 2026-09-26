package com.processVisualisation.virtualKitchen.ai.model;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
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

    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;
}
