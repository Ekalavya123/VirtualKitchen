package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJob;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.VisualizationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.BatchResult;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskResult;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Orchestrates async, per-step recipe visualization generation on top of the generic
 * {@link TaskPool} framework. A job is created synchronously (fast — one Mongo read/write),
 * then the actual generation work is fanned out across the bounded "visualization" pool while a
 * separate "orchestrator" pool runs the coordinating logic, so the calling (HTTP request) thread
 * never blocks on the slow AI/image-generation calls.
 */
@Service
public class VisualizationJobService {

    private static final Logger log = LoggerFactory.getLogger(VisualizationJobService.class);

    private final AIRecipeVisualizationService aiRecipeVisualizationService;
    private final VisualizationJobRepository visualizationJobRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskPool visualizationTaskPool;
    private final TaskPool visualizationOrchestratorTaskPool;

    public VisualizationJobService(
            AIRecipeVisualizationService aiRecipeVisualizationService,
            VisualizationJobRepository visualizationJobRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("visualizationTaskPool") TaskPool visualizationTaskPool,
            @Qualifier("visualizationOrchestratorTaskPool") TaskPool visualizationOrchestratorTaskPool
    ) {
        this.aiRecipeVisualizationService = aiRecipeVisualizationService;
        this.visualizationJobRepository = visualizationJobRepository;
        this.mongoTemplate = mongoTemplate;
        this.visualizationTaskPool = visualizationTaskPool;
        this.visualizationOrchestratorTaskPool = visualizationOrchestratorTaskPool;
    }

    /**
     * Validates the recipe and creates a {@code QUEUED} job synchronously (so a missing recipe
     * fails fast, before any job exists), then hands the actual generation off to the
     * orchestrator pool and returns immediately.
     *
     * @param recipeId identifier of the recipe flow to visualize
     * @return the newly created job in {@code QUEUED} status
     * @throws RecipeFlowGenerationException if the recipe flow cannot be found
     */
    public VisualizationJobResponseDTO startJob(String recipeId) {
        AIRecipeVisualizationService.RecipeStepPreparation preparation =
                aiRecipeVisualizationService.prepareStepContexts(recipeId);

        VisualizationJob job = new VisualizationJob();
        job.setId(java.util.UUID.randomUUID().toString());
        job.setRecipeId(recipeId);
        job.setStatus(VisualizationJobStatus.QUEUED);
        job.setTotalSteps(preparation.steps().size());
        job.setCompletedSteps(0);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        visualizationJobRepository.save(job);

        visualizationOrchestratorTaskPool.submit(new NamedTask<>(job.getId(), () -> {
            runPipeline(job.getId(), preparation);
            return null;
        }));

        return toDto(job);
    }

    /**
     * Retrieves the current status and per-step results of a visualization job.
     *
     * @param jobId identifier of the job to look up
     * @return the current job status as a DTO
     * @throws RecipeFlowGenerationException if no job exists with the given id
     */
    public VisualizationJobResponseDTO getJobStatus(String jobId) {
        VisualizationJob job = visualizationJobRepository.findById(jobId)
                .orElseThrow(() -> new RecipeFlowGenerationException("Visualization job not found: " + jobId));
        return toDto(job);
    }

    private void runPipeline(String jobId, AIRecipeVisualizationService.RecipeStepPreparation preparation) {
        markStatus(jobId, VisualizationJobStatus.IN_PROGRESS, Map.of("startedAt", Instant.now()));
        try {
            List<NamedTask<VisualizationAsset>> tasks = preparation.steps().stream()
                    .map(ctx -> new NamedTask<VisualizationAsset>(
                            ctx.node().getId(),
                            () -> aiRecipeVisualizationService.resolveVisualizationAsset(
                                    ctx.data(), ctx.stepFields(), ctx.previousStepFields())
                    ))
                    .toList();

            BatchResult<VisualizationAsset> batch = visualizationTaskPool.submitAll(
                    tasks, result -> recordStepResult(jobId, result));

            // Attach every asset that was actually produced, even ones with a null imageUrl —
            // generateImage() (AIRecipeVisualizationService) already swallows image-generation
            // errors internally and returns such an asset rather than throwing, so a null
            // imageUrl is the common failure signal here, not a thrown exception.
            Map<String, VisualizationAsset> assetsByStepId = batch.results().stream()
                    .filter(result -> result.isSuccess() && result.value() != null)
                    .collect(Collectors.toMap(TaskResult::taskId, TaskResult::value));

            aiRecipeVisualizationService.attachResultsAndSave(preparation.flow(), assetsByStepId);

            boolean anyStepFailed = batch.results().stream().anyMatch(result -> !isEffectivelySuccessful(result));
            VisualizationJobStatus finalStatus = anyStepFailed
                    ? VisualizationJobStatus.COMPLETED_WITH_ERRORS
                    : VisualizationJobStatus.COMPLETED;
            markStatus(jobId, finalStatus, Map.of("completedAt", Instant.now()));
        } catch (Exception e) {
            // Anything outside the per-step task boundary (e.g. the final save itself throwing)
            // must still terminate the job — otherwise a polling client would spin forever on a
            // job stuck IN_PROGRESS with no writer left to unstick it.
            log.error("Visualization job {} failed", jobId, e);
            Update update = new Update()
                    .set("status", VisualizationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, VisualizationJob.class);
        }
    }

    private void recordStepResult(String jobId, TaskResult<VisualizationAsset> result) {
        VisualizationJob.StepResult stepResult = new VisualizationJob.StepResult();
        stepResult.setStepId(result.taskId());
        boolean success = isEffectivelySuccessful(result);
        stepResult.setSuccess(success);
        if (result.isSuccess() && result.value() != null) {
            VisualizationAsset asset = result.value();
            stepResult.setVisualizationAssetId(asset.getId());
            stepResult.setImageUrl(asset.getImageUrl());
            if (!success) {
                stepResult.setErrorMessage("Image generation failed for this step");
            }
        } else {
            stepResult.setErrorMessage(safeMessage(result.error()));
        }

        Update update = new Update()
                .push("stepResults", stepResult)
                .inc("completedSteps", 1)
                .set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, VisualizationJob.class);
    }

    /**
     * A task can come back "successful" (no exception) yet still represent a failed step:
     * {@code generateImage} in {@link AIRecipeVisualizationService} already catches
     * image-generation/upload errors internally and returns an asset with a null
     * {@code imageUrl} rather than throwing. Treat that the same as a thrown exception.
     */
    private boolean isEffectivelySuccessful(TaskResult<VisualizationAsset> result) {
        return result.isSuccess() && result.value() != null && result.value().getImageUrl() != null;
    }

    private void markStatus(String jobId, VisualizationJobStatus status, Map<String, Object> extraFields) {
        Update update = new Update().set("status", status).set("updatedAt", Instant.now());
        extraFields.forEach(update::set);
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, VisualizationJob.class);
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private VisualizationJobResponseDTO toDto(VisualizationJob job) {
        List<VisualizationJobResponseDTO.StepResultDTO> steps = job.getStepResults().stream()
                .map(sr -> VisualizationJobResponseDTO.StepResultDTO.builder()
                        .stepId(sr.getStepId())
                        .success(sr.isSuccess())
                        .visualizationAssetId(sr.getVisualizationAssetId())
                        .imageUrl(sr.getImageUrl())
                        .errorMessage(sr.getErrorMessage())
                        .build())
                .toList();

        return VisualizationJobResponseDTO.builder()
                .jobId(job.getId())
                .recipeId(job.getRecipeId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .totalSteps(job.getTotalSteps())
                .completedSteps(job.getCompletedSteps())
                .steps(steps)
                .build();
    }
}
