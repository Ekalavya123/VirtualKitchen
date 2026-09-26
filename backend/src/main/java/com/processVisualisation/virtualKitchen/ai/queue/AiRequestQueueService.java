package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.routing.FallbackReason;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskResult;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The admission-control, job-persistence, and retry/backoff layer every AI
 * request goes through, on top of {@link ModelSelectionService} (which model)
 * and {@link CreditService} (whose credits). Two execution modes are offered:
 * <ul>
 *     <li>{@link #executeBounded} submits the work to the dedicated {@code
 *     "ai-text"} {@link TaskPool} and blocks the caller up to a configured
 *     timeout. Used for text-to-text (recipe generation, chat), which today
 *     runs entirely unbounded on the servlet thread.</li>
 *     <li>{@link #executeInline} runs the work directly on the calling
 *     thread, with no nested pool submission. Used for text-to-image, whose
 *     per-step calls already run inside the existing {@code "visualization"}
 *     pool's own bounded worker threads (via {@code RecipeProcessVisualizationJobService})
 *     — submitting to that same pool again from within one of its own
 *     workers would risk a saturated pool deadlocking against itself, exactly
 *     what {@code TaskPoolConfig} already keeps the orchestrator/worker pools
 *     separate to avoid.</li>
 * </ul>
 * Both modes share the same admission check, job lifecycle, and retry policy;
 * only how the work is actually invoked differs.
 * <p>
 * A duplicate submission (same {@code idempotencyKey} for the same user) is
 * deliberately left to surface as the Mongo {@code DuplicateKeyException}
 * that {@code AiRequestJob}'s compound unique index throws — {@code
 * GlobalExceptionHandler} already maps that to HTTP 409, so no reservation
 * or extra job row is ever created for it.
 */
@Service
public class AiRequestQueueService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestQueueService.class);

    private static final String AI_TEXT_POOL = "ai-text";
    private static final String AI_IMAGE_POOL = "visualization";

    private final AiRequestJobRepository jobRepository;
    private final ModelSelectionService modelSelectionService;
    private final CreditService creditService;
    private final TaskPool aiTextTaskPool;
    private final AiQueueProperties queueProperties;
    private final AiRequestProperties requestProperties;
    private final AiArtifactService artifactService;
    private final AiModelRegistry modelRegistry;

    private final Map<String, AtomicInteger> inFlightByPool = new ConcurrentHashMap<>();

    public AiRequestQueueService(
            AiRequestJobRepository jobRepository,
            ModelSelectionService modelSelectionService,
            CreditService creditService,
            @Qualifier("aiTextTaskPool") TaskPool aiTextTaskPool,
            AiQueueProperties queueProperties,
            AiRequestProperties requestProperties,
            AiArtifactService artifactService,
            AiModelRegistry modelRegistry
    ) {
        this.jobRepository = jobRepository;
        this.modelSelectionService = modelSelectionService;
        this.creditService = creditService;
        this.aiTextTaskPool = aiTextTaskPool;
        this.queueProperties = queueProperties;
        this.requestProperties = requestProperties;
        this.artifactService = artifactService;
        this.modelRegistry = modelRegistry;
    }

    /** Bounded, pool-submitted execution — see class javadoc. Used for {@link AiCapability#TEXT_TO_TEXT}. */
    public <R> AiRequestOutcome<R> executeBounded(
            Long userId, AiCapability capability, String preferredModelKey, String idempotencyKey,
            String correlationType, String correlationId, AiWork<R> work
    ) {
        return executeBounded(userId, capability, preferredModelKey, idempotencyKey,
                correlationType, correlationId, null, work);
    }

    /**
     * Bounded execution with AI artifact reuse and staging.
     * <p>
     * Passing an {@code artifactSpec} opts this call site into the artifact store: a stored
     * payload is served instead of calling the provider, and a fresh payload is persisted before
     * this method returns. Passing {@code null} is exactly the previous behaviour.
     *
     * @param artifactSpec the artifact key, codec, and consumer for this call site, or null to opt out
     */
    public <R> AiRequestOutcome<R> executeBounded(
            Long userId, AiCapability capability, String preferredModelKey, String idempotencyKey,
            String correlationType, String correlationId, AiArtifactSpec<R> artifactSpec, AiWork<R> work
    ) {
        Optional<AiRequestOutcome<R>> reused = findReusable(userId, artifactSpec);
        if (reused.isPresent()) {
            return reused.get();
        }

        return withAdmissionControl(AI_TEXT_POOL, capability, () -> {
            AiRequestJob job = createJob(userId, capability, preferredModelKey, idempotencyKey, correlationType, correlationId);
            ModelSelectionOutcome selection = beginProcessing(job, capability, preferredModelKey);
            long timeoutMs = requestProperties.timeoutFor(capability, 30_000L);

            AiRequestOutcome<R> outcome = runWithRetries(job, selection, () -> {
                CompletableFuture<TaskResult<R>> future =
                        aiTextTaskPool.submit(new NamedTask<>(job.getId(), () -> work.run(selection)));
                TaskResult<R> result;
                try {
                    result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (ExecutionException ee) {
                    throw ee.getCause() instanceof Exception ex ? ex : ee;
                }
                if (!result.isSuccess()) {
                    Throwable error = result.error();
                    throw error instanceof Exception ex ? ex : new RuntimeException(error);
                }
                return result.value();
            });

            return withStagedArtifact(userId, capability, correlationType, correlationId, artifactSpec, outcome);
        });
    }

    /** Inline, calling-thread execution — see class javadoc. Used for {@link AiCapability#TEXT_TO_IMAGE}. */
    public <R> AiRequestOutcome<R> executeInline(
            Long userId, AiCapability capability, String preferredModelKey, String idempotencyKey,
            String correlationType, String correlationId, AiWork<R> work
    ) {
        return executeInline(userId, capability, preferredModelKey, idempotencyKey,
                correlationType, correlationId, null, work);
    }

    /**
     * Inline execution with AI artifact reuse and staging.
     * <p>
     * Passing an {@code artifactSpec} opts this call site into the artifact store; passing
     * {@code null} is exactly the previous behaviour.
     *
     * @param artifactSpec the artifact key, codec, and consumer for this call site, or null to opt out
     */
    public <R> AiRequestOutcome<R> executeInline(
            Long userId, AiCapability capability, String preferredModelKey, String idempotencyKey,
            String correlationType, String correlationId, AiArtifactSpec<R> artifactSpec, AiWork<R> work
    ) {
        Optional<AiRequestOutcome<R>> reused = findReusable(userId, artifactSpec);
        if (reused.isPresent()) {
            return reused.get();
        }

        return withAdmissionControl(AI_IMAGE_POOL, capability, () -> {
            AiRequestJob job = createJob(userId, capability, preferredModelKey, idempotencyKey, correlationType, correlationId);
            ModelSelectionOutcome selection = beginProcessing(job, capability, preferredModelKey);

            AiRequestOutcome<R> outcome = runWithRetries(job, selection, () -> work.run(selection));
            return withStagedArtifact(userId, capability, correlationType, correlationId, artifactSpec, outcome);
        });
    }

    /**
     * Read-before-spend: serves a stored payload instead of calling the provider.
     * <p>
     * This runs <b>before</b> {@link #withAdmissionControl} and therefore before
     * {@link #beginProcessing}, which is where {@code ModelSelectionService#select} reserves
     * credits. A reuse check placed any later — inside the {@link AiWork} lambda, say — would
     * still have reserved and consumed a credit, defeating the point of the store.
     * <p>
     * It also sits outside admission control on purpose: a hit performs no provider work, so it
     * must not be rejected with {@link AiQueueFullException} while the queue is saturated. A burst
     * of retried-after-failure requests should be absorbed by the store, not bounced by it.
     * <p>
     * No {@code AiRequestJob} row is written for a hit, so {@code ai_request_jobs} keeps meaning
     * "a provider call we actually made"; reuse is observable via {@code AiArtifact.reuseCount}.
     */
    private <R> Optional<AiRequestOutcome<R>> findReusable(Long userId, AiArtifactSpec<R> artifactSpec) {
        if (artifactSpec == null) {
            return Optional.empty();
        }
        return artifactService.findReusable(userId, artifactSpec)
                .map(hit -> AiRequestOutcome.reused(hit, reusedSelection(hit.artifact())));
    }

    /**
     * Rebuilds the model selection for a reused payload from the artifact's recorded metadata.
     * <p>
     * The reservation is always {@code null}: the payload was paid for once, when it was produced,
     * so nothing downstream may consume or release credits against it.
     */
    private ModelSelectionOutcome reusedSelection(AiArtifact artifact) {
        ModelDefinition model = modelRegistry.find(artifact.getProducedByModelKey())
                .orElseGet(() -> syntheticDefinition(artifact));
        return new ModelSelectionOutcome(model, artifact.isUsedFallback(), FallbackReason.NONE, null);
    }

    /**
     * Stands in for a model that has since been removed from configuration, so an artifact
     * produced by it stays reusable rather than being stranded.
     */
    private ModelDefinition syntheticDefinition(AiArtifact artifact) {
        ModelDefinition definition = new ModelDefinition();
        definition.setKey(artifact.getProducedByModelKey());
        definition.setCapability(artifact.getCapability());
        definition.setTier(artifact.getProducedByTier());
        definition.setCreditCost(artifact.getCreditCost());
        definition.setEnabled(false);
        return definition;
    }

    /**
     * Write-before-dependency: persists the payload before the caller — and therefore before any
     * dependent work — can touch it.
     * <p>
     * Staging failures are logged and swallowed. Failing an otherwise-successful <em>paid</em> call
     * in order to report a bookkeeping failure would be strictly worse than the behaviour this
     * feature replaces; the caller simply proceeds without a retained payload.
     */
    private <R> AiRequestOutcome<R> withStagedArtifact(
            Long userId, AiCapability capability, String correlationType, String correlationId,
            AiArtifactSpec<R> artifactSpec, AiRequestOutcome<R> outcome) {

        if (artifactSpec == null) {
            return outcome;
        }
        try {
            AiArtifact artifact = artifactService.stage(userId, capability, correlationType, correlationId,
                    artifactSpec, outcome.value(), outcome.selection(), outcome.jobId());
            return AiRequestOutcome.fresh(outcome.jobId(), outcome.value(), outcome.selection(), artifact);
        } catch (Exception e) {
            log.error("Could not stage AI artifact for job {} (key={}); the payload is not retained",
                    outcome.jobId(), artifactSpec.artifactKey(), e);
            return outcome;
        }
    }

    private <R> AiRequestOutcome<R> withAdmissionControl(String poolName, AiCapability capability,
                                                          java.util.function.Supplier<AiRequestOutcome<R>> body) {
        AtomicInteger counter = inFlightByPool.computeIfAbsent(poolName, k -> new AtomicInteger());
        int maxDepth = poolName.equals(AI_TEXT_POOL) ? queueProperties.getAiTextMaxDepth() : queueProperties.getAiImageMaxDepth();

        if (counter.incrementAndGet() > maxDepth) {
            counter.decrementAndGet();
            throw new AiQueueFullException(capability);
        }
        try {
            return body.get();
        } finally {
            counter.decrementAndGet();
        }
    }

    private AiRequestJob createJob(Long userId, AiCapability capability, String preferredModelKey,
                                    String idempotencyKey, String correlationType, String correlationId) {
        AiRequestJob job = new AiRequestJob();
        job.setId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setCapability(capability);
        job.setRequestedModelKey(preferredModelKey);
        job.setIdempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString());
        job.setStatus(AiRequestStatus.QUEUED);
        job.setAttempt(0);
        job.setMaxAttempts(requestProperties.getMaxRetries() + 1);
        job.setCorrelationType(correlationType);
        job.setCorrelationId(correlationId);
        job.setQueuedAt(Instant.now());
        // A DuplicateKeyException here (same userId+idempotencyKey already exists) is intentionally
        // left to propagate to GlobalExceptionHandler's existing 409 handler rather than caught here —
        // see class javadoc.
        return jobRepository.save(job);
    }

    private ModelSelectionOutcome beginProcessing(AiRequestJob job, AiCapability capability, String preferredModelKey) {
        ModelSelectionOutcome selection;
        try {
            selection = modelSelectionService.select(job.getUserId(), capability, preferredModelKey);
        } catch (RuntimeException ex) {
            job.setStatus(AiRequestStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
            throw ex;
        }

        job.setResolvedModelKey(selection.model().getKey());
        job.setResolvedTier(selection.model().getTier());
        job.setUsedFallback(selection.usedFallback());
        job.setFallbackReason(selection.fallbackReason().name());
        job.setCreditCost(selection.reservation() != null ? selection.reservation().cost() : 0);
        job.setCreditTransactionId(selection.reservation() != null ? selection.reservation().transactionId() : null);
        job.setStatus(AiRequestStatus.PROCESSING);
        job.setStartedAt(Instant.now());
        job.setAttempt(1);
        jobRepository.save(job);
        return selection;
    }

    private <R> AiRequestOutcome<R> runWithRetries(AiRequestJob job, ModelSelectionOutcome selection, Attempt<R> attempt) {
        int maxAttempts = job.getMaxAttempts();
        Exception lastError = null;

        for (int i = 1; i <= maxAttempts; i++) {
            job.setAttempt(i);
            try {
                R value = attempt.run();
                if (selection.reservation() != null) {
                    creditService.consume(job.getUserId(), selection.reservation(), job.getId());
                }
                job.setStatus(AiRequestStatus.COMPLETED);
                job.setCompletedAt(Instant.now());
                jobRepository.save(job);
                return AiRequestOutcome.fresh(job.getId(), value, selection, null);
            } catch (TimeoutException te) {
                lastError = te;
            } catch (Exception e) {
                lastError = e;
            }

            if (!isRetryable(lastError) || i == maxAttempts) {
                break;
            }
            log.warn("AI request {} attempt {} failed transiently, retrying: {}", job.getId(), i, lastError.getMessage());
            sleepBackoff();
        }

        if (selection.reservation() != null) {
            creditService.release(job.getUserId(), selection.reservation(), job.getId());
        }
        job.setStatus(AiRequestStatus.FAILED);
        job.setErrorMessage(lastError != null ? lastError.getMessage() : "Unknown failure");
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        if (lastError instanceof RuntimeException re) {
            throw re;
        }
        throw new AiRequestTimeoutException(job.getId());
    }

    private boolean isRetryable(Throwable error) {
        return error instanceof AITimeoutException || error instanceof AICommunicationException;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(requestProperties.getRetryBackoffMs());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Attempt<R> {
        R run() throws Exception;
    }
}
