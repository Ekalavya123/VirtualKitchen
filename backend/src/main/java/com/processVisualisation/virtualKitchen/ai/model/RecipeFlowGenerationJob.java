package com.processVisualisation.virtualKitchen.ai.model;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Tracks the progress of an async AI recipe-flow generation run started via
 * {@code RecipeFlowGenerationJobService.startJob}. Unlike {@link VisualizationJob},
 * there is exactly one writer at a time (no fan-out across concurrent steps),
 * so stage/status updates are plain single-document {@code $set} writes.
 */
@Data
@Document(collection = "recipe_flow_generation_job")
public class RecipeFlowGenerationJob {

    @Id
    private String id;

    @Indexed
    private Long userId;

    @Indexed
    private String clientRequestId;

    private RecipeFlowGenerationJobStatus status;

    private RecipeFlowGenerationStage stage;

    private RecipeFlowGenerationResponseDTO result;

    private String errorMessage;

    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;
}
