package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationStage;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.RecipeProcessGenerationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Orchestrates async AI Process generation on top of the generic
 * {@link TaskPool} framework, using the "flow-generation-orchestrator" task
 * pool (this is a comparable one-LLM-call, single-writer job, not a distinct
 * concurrency profile that would justify a new pool). A job is created
 * synchronously (one fast Mongo write), then the actual generation work runs
 * on the shared pool so the calling HTTP thread never blocks on it.
 */
@Service
public class RecipeProcessGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(RecipeProcessGenerationJobService.class);

    private final RecipeProcessGenerationService recipeProcessGenerationService;
    private final RecipeProcessGenerationJobRepository jobRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskPool flowGenerationOrchestratorTaskPool;

    public RecipeProcessGenerationJobService(
            RecipeProcessGenerationService recipeProcessGenerationService,
            RecipeProcessGenerationJobRepository jobRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("flowGenerationOrchestratorTaskPool") TaskPool flowGenerationOrchestratorTaskPool
    ) {
        this.recipeProcessGenerationService = recipeProcessGenerationService;
        this.jobRepository = jobRepository;
        this.mongoTemplate = mongoTemplate;
        this.flowGenerationOrchestratorTaskPool = flowGenerationOrchestratorTaskPool;
    }

    /**
     * Starts (or resumes) an async Process generation job for the given user/recipe and returns
     * immediately. If {@code clientRequestId} matches a job already started by this user, that
     * existing job is returned as-is rather than starting a duplicate.
     */
    public RecipeProcessGenerationJobResponseDTO startJob(Long userId, Long recipeId, String recipeText, String clientRequestId) {
        if (StringUtils.hasText(clientRequestId)) {
            var existing = jobRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }

        RecipeProcessGenerationJob job = new RecipeProcessGenerationJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setRecipeId(recipeId);
        job.setClientRequestId(clientRequestId);
        job.setStatus(RecipeProcessGenerationJobStatus.QUEUED);
        job.setStage(RecipeProcessGenerationStage.QUEUED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        jobRepository.save(job);

        flowGenerationOrchestratorTaskPool.submit(new NamedTask<>(job.getId(), () -> {
            runPipeline(userId, job.getId(), recipeText, clientRequestId);
            return null;
        }));

        return toDto(job);
    }

    /**
     * Retrieves the current status/progress/result of a previously started job.
     *
     * @throws RecipeProcessAiException if no job exists with the given id
     */
    public RecipeProcessGenerationJobResponseDTO getJobStatus(String jobId) {
        RecipeProcessGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RecipeProcessAiException("Process generation job not found: " + jobId));
        return toDto(job);
    }

    private void runPipeline(Long userId, String jobId, String recipeText, String clientRequestId) {
        markStatus(jobId, RecipeProcessGenerationJobStatus.IN_PROGRESS, RecipeProcessGenerationStage.QUEUED,
                Map.of("startedAt", Instant.now()));
        try {
            RecipeProcessGenerationResultDTO result = recipeProcessGenerationService.generate(
                    userId, recipeText, clientRequestId, stage -> updateStage(jobId, stage));

            Update update = new Update()
                    .set("status", RecipeProcessGenerationJobStatus.COMPLETED)
                    .set("stage", RecipeProcessGenerationStage.COMPLETED)
                    .set("result", result)
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
        } catch (Exception e) {
            // Anything the pipeline throws (validation failure after retry, AI timeout, an
            // unexpected error) must still terminate the job — otherwise a polling client would
            // spin forever on a job stuck IN_PROGRESS with no writer left to unstick it.
            log.error("Process generation job {} failed", jobId, e);
            Update update = new Update()
                    .set("status", RecipeProcessGenerationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
        }
    }

    private void updateStage(String jobId, RecipeProcessGenerationStage stage) {
        Update update = new Update().set("stage", stage).set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
    }

    private void markStatus(String jobId, RecipeProcessGenerationJobStatus status, RecipeProcessGenerationStage stage, Map<String, Object> extraFields) {
        Update update = new Update().set("status", status).set("stage", stage).set("updatedAt", Instant.now());
        extraFields.forEach(update::set);
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private RecipeProcessGenerationJobResponseDTO toDto(RecipeProcessGenerationJob job) {
        RecipeProcessGenerationStage stage = job.getStage() != null ? job.getStage() : RecipeProcessGenerationStage.QUEUED;
        return RecipeProcessGenerationJobResponseDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .stage(stage.name())
                .progressPercent(stage.getPercent())
                .result(job.getResult())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
