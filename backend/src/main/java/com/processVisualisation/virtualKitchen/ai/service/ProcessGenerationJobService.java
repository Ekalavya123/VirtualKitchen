package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.ProcessGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.ProcessGenerationStage;
import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.ProcessGenerationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationResultDTO;
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
 * {@link TaskPool} framework — mirrors {@link RecipeFlowGenerationJobService}
 * exactly, including reusing the same "flow-generation-orchestrator" task
 * pool (this is a comparable one-LLM-call, single-writer job, not a distinct
 * concurrency profile that would justify a new pool). A job is created
 * synchronously (one fast Mongo write), then the actual generation work runs
 * on the shared pool so the calling HTTP thread never blocks on it.
 */
@Service
public class ProcessGenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(ProcessGenerationJobService.class);

    private final ProcessGenerationService processGenerationService;
    private final ProcessGenerationJobRepository jobRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskPool flowGenerationOrchestratorTaskPool;

    public ProcessGenerationJobService(
            ProcessGenerationService processGenerationService,
            ProcessGenerationJobRepository jobRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("flowGenerationOrchestratorTaskPool") TaskPool flowGenerationOrchestratorTaskPool
    ) {
        this.processGenerationService = processGenerationService;
        this.jobRepository = jobRepository;
        this.mongoTemplate = mongoTemplate;
        this.flowGenerationOrchestratorTaskPool = flowGenerationOrchestratorTaskPool;
    }

    /**
     * Starts (or resumes) an async Process generation job for the given user/recipe and returns
     * immediately. If {@code clientRequestId} matches a job already started by this user, that
     * existing job is returned as-is rather than starting a duplicate.
     */
    public ProcessGenerationJobResponseDTO startJob(Long userId, Long recipeId, String recipeText, String clientRequestId) {
        if (StringUtils.hasText(clientRequestId)) {
            var existing = jobRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }

        ProcessGenerationJob job = new ProcessGenerationJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setRecipeId(recipeId);
        job.setClientRequestId(clientRequestId);
        job.setStatus(RecipeFlowGenerationJobStatus.QUEUED);
        job.setStage(ProcessGenerationStage.QUEUED);
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
    public ProcessGenerationJobResponseDTO getJobStatus(String jobId) {
        ProcessGenerationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RecipeFlowGenerationException("Process generation job not found: " + jobId));
        return toDto(job);
    }

    private void runPipeline(Long userId, String jobId, String recipeText, String clientRequestId) {
        markStatus(jobId, RecipeFlowGenerationJobStatus.IN_PROGRESS, ProcessGenerationStage.QUEUED,
                Map.of("startedAt", Instant.now()));
        try {
            ProcessGenerationResultDTO result = processGenerationService.generate(
                    userId, recipeText, clientRequestId, stage -> updateStage(jobId, stage));

            Update update = new Update()
                    .set("status", RecipeFlowGenerationJobStatus.COMPLETED)
                    .set("stage", ProcessGenerationStage.COMPLETED)
                    .set("result", result)
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, ProcessGenerationJob.class);
        } catch (Exception e) {
            // Anything the pipeline throws (validation failure after retry, AI timeout, an
            // unexpected error) must still terminate the job — otherwise a polling client would
            // spin forever on a job stuck IN_PROGRESS with no writer left to unstick it.
            log.error("Process generation job {} failed", jobId, e);
            Update update = new Update()
                    .set("status", RecipeFlowGenerationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, ProcessGenerationJob.class);
        }
    }

    private void updateStage(String jobId, ProcessGenerationStage stage) {
        Update update = new Update().set("stage", stage).set("updatedAt", Instant.now());
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, ProcessGenerationJob.class);
    }

    private void markStatus(String jobId, RecipeFlowGenerationJobStatus status, ProcessGenerationStage stage, Map<String, Object> extraFields) {
        Update update = new Update().set("status", status).set("stage", stage).set("updatedAt", Instant.now());
        extraFields.forEach(update::set);
        mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, ProcessGenerationJob.class);
    }

    private String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message != null ? message : t.getClass().getSimpleName();
    }

    private ProcessGenerationJobResponseDTO toDto(ProcessGenerationJob job) {
        ProcessGenerationStage stage = job.getStage() != null ? job.getStage() : ProcessGenerationStage.QUEUED;
        return ProcessGenerationJobResponseDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .stage(stage.name())
                .progressPercent(stage.getPercent())
                .result(job.getResult())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}
