package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationStage;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.RecipeProcessGenerationJobRepository;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.logging.FailureLogger;
import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessGenerationResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
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

    /** How long a finished-but-unapplied generation result is still offered back to its user. */
    static final Duration UNAPPLIED_RESULT_TTL = Duration.ofHours(24);

    /**
     * Starts an async Process generation job for the given user/recipe and returns immediately.
     * At most one job runs per user+recipe: if one is already QUEUED/IN_PROGRESS (or
     * {@code clientRequestId} matches a job this user already started), that job is returned with
     * {@code reused=true} instead of starting (and paying for) a duplicate.
     */
    public RecipeProcessGenerationJobResponseDTO startJob(Long userId, Long recipeId, String recipeText, String clientRequestId) {
        if (StringUtils.hasText(clientRequestId)) {
            var existing = jobRepository.findByUserIdAndClientRequestId(userId, clientRequestId);
            if (existing.isPresent()) {
                log.info("event=process_generation_job_reused reason=client_request_id jobId={} recipeId={}", existing.get().getId(), recipeId);
                return toDto(existing.get(), true);
            }
        }

        String activeKey = activeKey(userId, recipeId);
        var running = jobRepository.findByActiveKey(activeKey);
        if (running.isPresent()) {
            log.info("event=process_generation_job_reused reason=already_running jobId={} recipeId={}", running.get().getId(), recipeId);
            return toDto(running.get(), true);
        }

        RecipeProcessGenerationJob job = new RecipeProcessGenerationJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setRecipeId(recipeId);
        job.setClientRequestId(clientRequestId);
        job.setActiveKey(activeKey);
        job.setStatus(RecipeProcessGenerationJobStatus.QUEUED);
        job.setStage(RecipeProcessGenerationStage.QUEUED);
        job.setCreatedAt(Instant.now());
        job.setUpdatedAt(Instant.now());
        try {
            jobRepository.insert(job);
        } catch (DuplicateKeyException e) {
            // Lost a race with a concurrent start for the same recipe: join the job that won.
            var winner = jobRepository.findByActiveKey(activeKey);
            if (winner.isPresent()) {
                log.info("event=process_generation_job_reused reason=concurrent_start jobId={} recipeId={}", winner.get().getId(), recipeId);
                return toDto(winner.get(), true);
            }
            throw e;
        }
        log.info("event=process_generation_job_started jobId={} recipeId={} inputChars={}",
                job.getId(), recipeId, recipeText == null ? 0 : recipeText.length());

        flowGenerationOrchestratorTaskPool.submit(new NamedTask<>(job.getId(), () -> {
            runPipeline(userId, job.getId(), recipeText, clientRequestId);
            return null;
        }));

        return toDto(job, false);
    }

    /**
     * Retrieves the current status/progress/result of a job the caller started for this recipe.
     *
     * @throws NoSuchElementException if no such job exists for this user and recipe
     */
    public RecipeProcessGenerationJobResponseDTO getJobStatus(Long userId, Long recipeId, String jobId) {
        return toDto(findOwnedJob(userId, recipeId, jobId), false);
    }

    /**
     * The generation the UI should attach to when the recipe tool opens: the running job, else
     * the newest completed job whose result was never loaded into the editor, else empty.
     */
    public Optional<RecipeProcessGenerationJobResponseDTO> findResumable(Long userId, Long recipeId) {
        Optional<RecipeProcessGenerationJob> job = jobRepository.findByActiveKey(activeKey(userId, recipeId));
        if (job.isEmpty()) {
            job = jobRepository.findFirstByUserIdAndRecipeIdAndStatusAndResultAppliedAtIsNullAndCompletedAtAfterOrderByCompletedAtDesc(
                    userId, recipeId, RecipeProcessGenerationJobStatus.COMPLETED, Instant.now().minus(UNAPPLIED_RESULT_TTL));
        }
        return job.map(found -> toDto(found, false));
    }

    /**
     * Records that the frontend loaded this job's result into the user's session, so it isn't
     * offered again on the next visit.
     *
     * @throws NoSuchElementException if no such job exists for this user and recipe
     */
    public void markResultApplied(Long userId, Long recipeId, String jobId) {
        findOwnedJob(userId, recipeId, jobId);
        mongoTemplate.updateFirst(query(where("_id").is(jobId).and("resultAppliedAt").is(null)),
                new Update().set("resultAppliedAt", Instant.now()), RecipeProcessGenerationJob.class);
    }

    static String activeKey(Long userId, Long recipeId) {
        return "gen:" + userId + ":" + recipeId;
    }

    private RecipeProcessGenerationJob findOwnedJob(Long userId, Long recipeId, String jobId) {
        // A job that belongs to another user or recipe is reported exactly like a missing one.
        return jobRepository.findById(jobId)
                .filter(job -> Objects.equals(job.getUserId(), userId) && Objects.equals(job.getRecipeId(), recipeId))
                .orElseThrow(() -> new NoSuchElementException("Process generation job not found: " + jobId));
    }

    private void runPipeline(Long userId, String jobId, String recipeText, String clientRequestId) {
        // Runs on the orchestrator pool; the pool restores the thread's MDC afterwards.
        MDC.put(MdcKeys.JOB_ID, jobId);
        long startedAt = System.nanoTime();
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
                    .set("updatedAt", Instant.now())
                    .unset("activeKey");
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
            log.info("event=process_generation_job_completed modelKey={} usedFallback={} subprocesses={} durationMs={}",
                    result.getModelUsed(), result.isUsedFallback(),
                    result.getSubprocesses() == null ? 0 : result.getSubprocesses().size(), elapsedMs(startedAt));
        } catch (Exception e) {
            // Anything the pipeline throws (validation failure after retry, AI timeout, an
            // unexpected error) must still terminate the job — otherwise a polling client would
            // spin forever on a job stuck IN_PROGRESS with no writer left to unstick it.
            FailureLogger.logFailure(log, "process_generation_job_failed", e);
            Update update = new Update()
                    .set("status", RecipeProcessGenerationJobStatus.FAILED)
                    .set("errorMessage", safeMessage(e))
                    .set("completedAt", Instant.now())
                    .set("updatedAt", Instant.now())
                    .unset("activeKey");
            mongoTemplate.updateFirst(query(where("_id").is(jobId)), update, RecipeProcessGenerationJob.class);
        }
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private void updateStage(String jobId, RecipeProcessGenerationStage stage) {
        log.debug("event=process_generation_stage_changed stage={}", stage);
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

    private RecipeProcessGenerationJobResponseDTO toDto(RecipeProcessGenerationJob job, boolean reused) {
        RecipeProcessGenerationStage stage = job.getStage() != null ? job.getStage() : RecipeProcessGenerationStage.QUEUED;
        return RecipeProcessGenerationJobResponseDTO.builder()
                .jobId(job.getId())
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .stage(stage.name())
                .progressPercent(stage.getPercent())
                .result(job.getResult())
                .errorMessage(job.getErrorMessage())
                .reused(reused)
                .build();
    }
}
