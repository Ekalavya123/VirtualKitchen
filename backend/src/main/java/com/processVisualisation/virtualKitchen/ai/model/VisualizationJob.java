package com.processVisualisation.virtualKitchen.ai.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the progress of an async recipe-visualization run started via
 * {@code VisualizationJobService.startJob}. {@code completedSteps} and
 * {@code stepResults} are updated atomically (Mongo {@code $inc}/{@code $push})
 * as steps complete concurrently, never via read-modify-write, since several
 * worker threads can finish steps for the same job at the same time.
 */
@Data
@Document(collection = "visualization_job")
public class VisualizationJob {

    @Id
    private String id;

    @Indexed
    private String recipeId;

    private VisualizationJobStatus status;

    private int totalSteps;

    private int completedSteps;

    private List<StepResult> stepResults = new ArrayList<>();

    private String errorMessage;

    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;

    @Data
    public static class StepResult {
        private String stepId;
        private boolean success;
        private Long visualizationAssetId;
        private String imageUrl;
        private String errorMessage;
    }
}
