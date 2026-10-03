package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJob;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.VisualizationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.BatchResult;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskResult;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.common.logging.FailureLogger;
import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Orchestrates async, per-step recipe process visualization on the {@code TaskPool} framework
 * (the bounded "visualization" worker pool plus the "orchestrator" pool, tracked in the
 * {@link VisualizationJob} collection), scoped to one process's own STEP nodes.
 */
@Service
public class RecipeProcessVisualizationJobService {

    private static final Logger log = LoggerFactory.getLogger(RecipeProcessVisualizationJobService.class);

    static final Set<VisualizationJobStatus> ACTIVE_STATUSES =
            EnumSet.of(VisualizationJobStatus.QUEUED, VisualizationJobStatus.IN_PROGRESS);

    private final RecipeProcessVisualizationService recipeProcessVisualizationService;
    private final VisualizationJobRepository visualizationJobRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskPool visualizationTaskPool;
    private final TaskPool visualizationOrchestratorTaskPool;

    public RecipeProcessVisualizationJobService(
            RecipeProcessVisualizationService recipeProcessVisualizationService,
            VisualizationJobRepository visualizationJobRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("visualizationTaskPool") TaskPool visualizationTaskPool,
            @Qualifier("visualizationOrchestratorTaskPool") TaskPool visualizationOrchestratorTaskPool
    ) {
        this.recipeProcessVisualizationService = recipeProcessVisualizationService;
        this.visualizationJobRepository = visualizationJobRepository;
        this.mongoTemplate = mongoTemplate;
        this.visualizationTaskPool = visualizationTaskPool;
        this.visualizationOrchestratorTaskPool = visualizationOrchestratorTaskPool;
    }

    /**
     * Validates the process and creates a {@code QUEUED} job synchronously (so a missing process
     * fails fast, before any job exists), then hands the actual generation off to the orchestrator
     * pool and returns immediately. At most one job runs per process: if one is already
     * QUEUED/IN_PROGRESS, that job is returned with {@code reused=true} instead of starting (and
     * paying for) a duplicate whose final save would race the first one's.
     *
     * @throws RecipeProcessAiException if the process cannot be found
     * @throws NoSuchElementException   if the process belongs to a different recipe
     */
    public VisualizationJobResponseDTO startJob(Long userId, Long recipeId, Long processId) {
        String activeKey = activeKey(processId);
        var running = visualizationJobRepository.findByActiveKey(activeKey);
        if (running.isPresent()) {
            log.info("event=visualization_job_reused reason=already_running jobId={} recipeId={} processId={}",
                    running.get().getId(), recipeId, processId);
            return toDto(running.get(), true);
        }

        RecipeProcessVisualizationService.ProcessStepPreparation preparation =
                recipeProcessVisualizationService.prepareStepContexts(processId);
        Long owningRecipeId = preparation.process().getRecipeId();
        if (owningRecipeId != null && !owningRecipeId.equals(recipeId)) {
            throw new NoSuchElementException("Process " + processId + " not found in recipe " + recipeId);
        }

        VisualizationJob job = new VisualizationJob();
        job.setId(java.util.UUID.randomUUID().toString());
        job.setRecipeId(String.valueOf(recipeId));
        job.setProcessId(processId);
        job.setUserId(userId);
        job.setActiveKey(activeKey);
        job.setStatus(VisualizationJobStatus.QUEUED);
        job.setTotalSteps(preparation.steps().size());
        job.setCompletedSteps(0);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        try {
            visualizationJobRepository.insert(job);
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent start for the same process: join the job that won.
            var winner = visualizationJobRepository.findByActiveKey(activeKey);
            if (winner.isPresent()) {
                log.info("event=visualization_job_reused reason=concurrent_start jobId={} recipeId={} processId={}",
                        winner.get().getId(), recipeId, processId);
                return toDto(winner.get(), true);
            }
            throw e;
        }
        log.info("event=visualization_job_started jobId={} recipeId={} processId={} steps={}",
                job.getId(), recipeId, processId, job.getTotalSteps());

        visualizationOrchestratorTaskPool.submit(new NamedTask<>(job.getId(), () -> {
            runPipeline(userId, recipeId, job.getId(), preparation);
            return null;
        }));

        return toDto(job, false);
    }

    /**
     * Retrieves the current status and per-step results of a visualization job the caller started
     * for this recipe/process.
     *
     * @throws NoSuchElementException if no such job exists for this user, recipe and process
     */
    public VisualizationJobResponseDTO getJobStatus(Long userId, Long recipeId, Long processId, String jobId) {
        VisualizationJob job = visualizationJobRepository.findById(jobId)
                .filter(found -> Objects.equals(found.getUserId(), userId)
                        && Objects.equals(found.getRecipeId(), String.valueOf(recipeId))
                        && Objects.equals(found.getProcessId(), processId))
                // A job that belongs to another user/recipe/process is reported exactly like a missing one.
                .orElseThrow(() -> new NoSuchElementException("Process visualization job not found: " + jobId));
        return toDto(job, false);
    }

    /**
     * Every visualization the caller has QUEUED/IN_PROGRESS for one of this recipe's processes,
     * so the UI can resume showing progress after a reload.
     */
    public List<VisualizationJobResponseDTO> findActive(Long userId, Long recipeId) {
        return visualizationJobRepository
                .findByRecipeIdAndStatusIn(String.valueOf(recipeId), ACTIVE_STATUSES)
                .stream()
                .filter(job -> Objects.equals(job.getUserId(), userId))
                .map(job -> toDto(job, false))
                .toList();
    }

    static String activeKey(Long processId) {
        return "viz:" + processId;
    }

    private void runPipeline(Long userId, Long recipeId, String jobId, RecipeProcessVisualizationService.ProcessStepPreparation preparation) {
        // Runs on the orchestrator pool; set before submitAll so every step task inherits the job ID.
        MDC.put(MdcKeys.JOB_ID, jobId);
        long startedAt = System.nanoTime();
        markStatus(jobId, VisualizationJobStatus.IN_PROGRESS, Map.of("startedAt", Instant.now()));
        try {
            List<NamedTask<VisualizationAsset>> tasks = preparation.steps().stream()
                    .map(ctx -> new NamedTask<VisualizationAsset>(
                            ctx.node().getId(),
                            () -> recipeProcessVisualizationService.resolveVisualizationAsset(
                                    userId, recipeId, ctx.node().getId(), ctx.input())
                    ))
                    .toList();

            BatchResult<VisualizationAsset> batch = visualizationTaskPool.submitAll(
                    tasks, result -> recordStepResult(jobId, result));

            // Attach every asset that was actually produced, even ones with a null imageUrl —
            // generateImage() already swallows image-generation errors internally and returns such
            // an asset rather than throwing, so a null imageUrl is the common failure signal here,
            // not a thrown exception.
            Map<String, VisualizationAsset> assetsByStepId = batch.results().stream()
                    .filter(result -> result.isSuccess() && result.value() != null)
                    .collect(Collectors.toMap(TaskResult::taskId, TaskResult::value));

            recipeProcessVisualizationService.attachResultsAndSave(preparation.process(), assetsByStepId);

            boolean anyStepFailed = batch.results().stream().anyMatch(result -> !isEffectivelySuccessful(result));
            VisualizationJobStatus finalStatus = anyStepFailed
                    ? VisualizationJobStatus.COMPLETED_WITH_ERRORS
                    : VisualizationJobStatus.COMPLETED;
            markStatus(jobId, finalStatus, Map.of("completedAt", Instant.now()));
            long failedSteps = batch.results().stream().filter(result -> !isEffectivelySuccessful(result)).count();
            if (anyStepFailed) {
                log.warn("event=visualization_job_completed status={} steps={} failedSteps={} durationMs={}",
                        finalStatus, batch.results().size(), failedSteps, (System.nanoTime() - startedAt) / 1_000_000L);
            } else {
                log.info("event=visualization_job_completed status={} steps={} failedSteps=0 durationMs={}",
                        finalStatus, batch.results().size(), (System.nanoTime() - startedAt) / 1_000_000L);
            }
        } catch (Exception e) {
            // Anything outside the per-step task boundary (e.g. the final save itself throwing)
            // must still terminate the job — otherwise a polling client would spin forever on a
            // job stuck IN_PROGRESS with no writer left to unstick it.
            FailureLogger.logFailure(log, "visualization_job_failed", e);
            Update update = new Update()
                    .set("status", VisualizationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now())
                    .unset("activeKey");
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
            stepResult.setModelKey(asset.getResolvedModelKey());
            stepResult.setTier(asset.getResolvedTier() != null ? asset.getResolvedTier().name() : null);
            stepResult.setUsedFallback(asset.isUsedFallback());
            if (!success) {
                stepResult.setErrorMessage(asset.getImageFailureReason() != null
                        ? asset.getImageFailureReason()
                        : "Image generation failed for this step");
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

    private boolean isEffectivelySuccessful(TaskResult<VisualizationAsset> result) {
        return result.isSuccess() && result.value() != null && result.value().getImageUrl() != null;
    }

    private void markStatus(String jobId, VisualizationJobStatus status, Map<String, Object> extraFields) {
        Update update = new Update().set("status", status).set("updatedAt", Instant.now());
        extraFields.forEach(update::set);
        if (!ACTIVE_STATUSES.contains(status)) {
            update.unset("activeKey");
        }
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, VisualizationJob.class);
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private VisualizationJobResponseDTO toDto(VisualizationJob job, boolean reused) {
        List<VisualizationJobResponseDTO.StepResultDTO> steps = job.getStepResults().stream()
                .map(sr -> VisualizationJobResponseDTO.StepResultDTO.builder()
                        .stepId(sr.getStepId())
                        .success(sr.isSuccess())
                        .visualizationAssetId(sr.getVisualizationAssetId())
                        .imageUrl(sr.getImageUrl())
                        .errorMessage(sr.getErrorMessage())
                        .modelKey(sr.getModelKey())
                        .tier(sr.getTier())
                        .usedFallback(sr.isUsedFallback())
                        .build())
                .toList();

        return VisualizationJobResponseDTO.builder()
                .jobId(job.getId())
                .recipeId(job.getRecipeId())
                .processId(job.getProcessId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .totalSteps(job.getTotalSteps())
                .completedSteps(job.getCompletedSteps())
                .steps(steps)
                .errorMessage(job.getErrorMessage())
                .reused(reused)
                .build();
    }
}
