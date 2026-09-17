package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJobStatus;
import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationStage;
import com.processVisualisation.virtualKitchen.ai.repository.RecipeFlowGenerationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
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
 * Orchestrates async recipe-flow generation on top of the generic {@link TaskPool} framework,
 * mirroring {@link VisualizationJobService}. A job is created synchronously (fast — one Mongo
 * write), then the actual generation work — including the potentially slow, bounded AI call — is
 * handed off to the "flow-generation-orchestrator" pool, so the calling (HTTP request) thread
 * never blocks on it. Since a single job has exactly one writer at a time (no concurrent
 * sub-steps), progress updates are plain single-document {@code $set} writes rather than the
 * atomic {@code $inc}/{@code $push} pattern {@link VisualizationJobService} needs.
 */
@Service
public class RecipeFlowGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(RecipeFlowGenerationJobService.class);

    private final AIRecipeGenerationService aiRecipeGenerationService;
    private final RecipeFlowGenerationJobRepository jobRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskPool flowGenerationOrchestratorTaskPool;

    public RecipeFlowGenerationJobService(
            AIRecipeGenerationService aiRecipeGenerationService,
            RecipeFlowGenerationJobRepository jobRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("flowGenerationOrchestratorTaskPool") TaskPool flowGenerationOrchestratorTaskPool
    ) {
        this.aiRecipeGenerationService = aiRecipeGenerationService;
        this.jobRepository = jobRepository;
        this.mongoTemplate = mongoTemplate;
        this.flowGenerationOrchestratorTaskPool = flowGenerationOrchestratorTaskPool;
    }

    /**
     * Starts (or resumes) an async recipe-flow generation job for the given user and returns
     * immediately. If {@code clientRequestId} matches a job already started by this user, that
     * existing job is returned as-is rather than starting a duplicate — the same idempotency
     * guarantee the synchronous endpoint already provides via {@code AiRequestJob}.
     */
    public RecipeFlowGenerationJobResponseDTO startJob(Long userId, String recipeText, String clientRequestId) {
        if (StringUtils.hasText(clientRequestId)) {
            var existing = jobRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }

        RecipeFlowGenerationJob job = new RecipeFlowGenerationJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setClientRequestId(clientRequestId);
        job.setStatus(RecipeFlowGenerationJobStatus.QUEUED);
        job.setStage(RecipeFlowGenerationStage.QUEUED);
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
     * @throws RecipeFlowGenerationException if no job exists with the given id
     */
    public RecipeFlowGenerationJobResponseDTO getJobStatus(String jobId) {
        RecipeFlowGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RecipeFlowGenerationException("Recipe flow generation job not found: " + jobId));
        return toDto(job);
    }

    private void runPipeline(Long userId, String jobId, String recipeText, String clientRequestId) {
        markStatus(jobId, RecipeFlowGenerationJobStatus.IN_PROGRESS, RecipeFlowGenerationStage.QUEUED,
                Map.of("startedAt", Instant.now()));
        try {
            RecipeFlowGenerationResponseDTO result = aiRecipeGenerationService.generateFlow(
                    userId, recipeText, clientRequestId, stage -> updateStage(jobId, stage));

            Update update = new Update()
                    .set("status", RecipeFlowGenerationJobStatus.COMPLETED)
                    .set("stage", RecipeFlowGenerationStage.COMPLETED)
                    .set("result", result)
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeFlowGenerationJob.class);
        } catch (Exception e) {
            // Anything the pipeline throws (validation failure after retry, AI timeout, an
            // unexpected error) must still terminate the job — otherwise a polling client would
            // spin forever on a job stuck IN_PROGRESS with no writer left to unstick it.
            log.error("Recipe flow generation job {} failed", jobId, e);
            Update update = new Update()
                    .set("status", RecipeFlowGenerationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeFlowGenerationJob.class);
        }
    }

    private void updateStage(String jobId, RecipeFlowGenerationStage stage) {
        Update update = new Update().set("stage", stage).set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeFlowGenerationJob.class);
    }

    private void markStatus(String jobId, RecipeFlowGenerationJobStatus status, RecipeFlowGenerationStage stage, Map<String, Object> extraFields) {
        Update update = new Update().set("status", status).set("stage", stage).set("updatedAt", Instant.now());
        extraFields.forEach(update::set);
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeFlowGenerationJob.class);
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private RecipeFlowGenerationJobResponseDTO toDto(RecipeFlowGenerationJob job) {
        RecipeFlowGenerationStage stage = job.getStage() != null ? job.getStage() : RecipeFlowGenerationStage.QUEUED;
        return RecipeFlowGenerationJobResponseDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .stage(stage.name())
                .progressPercent(stage.getPercent())
                .result(job.getResult())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
