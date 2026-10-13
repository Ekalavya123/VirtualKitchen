package com.processVisualisation.virtualKitchen.ai.analytics.backfill;

import com.processVisualisation.virtualKitchen.ai.analytics.AiAnalyticsStore;
import com.processVisualisation.virtualKitchen.ai.analytics.CorrelationResolver;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.analytics.model.UsageBasis;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * One-off, rerunnable recovery of analytics for AI requests made before instrumentation existed.
 * <p>
 * Reads {@code ai_request_jobs} (every queued provider request) and chat {@code ai_responses}; links them to
 * recipes, workflows, artifacts and outputs through identifiers that already exist; and inserts one historical
 * record per request with insert-if-absent semantics, so a rerun never duplicates or overwrites anything — live
 * records included. Source collections are only read. Triggered by an admin through the API, never at start-up.
 */
@Service
public class AiAnalyticsBackfillService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalyticsBackfillService.class);
    private static final int MAX_FAILURES_LISTED = 50;

    private final MongoTemplate mongoTemplate;
    private final AiAnalyticsStore store;
    private final CorrelationResolver correlationResolver;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public AiAnalyticsBackfillService(MongoTemplate mongoTemplate, AiAnalyticsStore store,
                                      CorrelationResolver correlationResolver) {
        this(mongoTemplate, store, correlationResolver, Clock.systemUTC());
    }

    AiAnalyticsBackfillService(MongoTemplate mongoTemplate, AiAnalyticsStore store,
                               CorrelationResolver correlationResolver, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.store = store;
        this.correlationResolver = correlationResolver;
        this.clock = clock;
    }

    /** Runs the backfill synchronously and returns its summary. Only one run at a time. */
    public BackfillRun run(Long triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A backfill is already running");
        }
        BackfillRun run = new BackfillRun();
        run.setId(UUID.randomUUID().toString());
        run.setStartedAt(clock.instant());
        run.setTriggeredBy(triggeredBy);
        run.setStatus("RUNNING");
        mongoTemplate.save(run);
        try {
            try (Stream<Document> jobs = mongoTemplate.stream(new Query(), Document.class, "ai_request_jobs")) {
                jobs.forEach(job -> importRequestJob(job, run));
            }
            Query chat = Query.query(where("context").is("chat").and("analyticsRequestId").exists(false));
            try (Stream<Document> responses = mongoTemplate.stream(chat, Document.class, "ai_responses")) {
                responses.forEach(response -> importChatResponse(response, run));
            }
            long unlinkable = mongoTemplate.count(Query.query(new Criteria().andOperator(
                    where("context").ne("chat"), where("analyticsRequestId").exists(false))), "ai_responses");
            run.setSkipped((int) unlinkable);
            if (unlinkable > 0) {
                run.getNotes().add(unlinkable + " generation/edit ai_responses records were not imported: they have no"
                        + " reliable link to a request, which is already represented by its ai_request_jobs row.");
            }
            run.setStatus("COMPLETED");
        } catch (RuntimeException e) {
            run.setStatus("FAILED");
            run.getFailures().add("run: " + e.getClass().getSimpleName());
            log.error("event=ai_analytics_backfill_failed runId={} errorType={}", run.getId(), e.getClass().getSimpleName(), e);
        } finally {
            run.setFinishedAt(clock.instant());
            mongoTemplate.save(run);
            running.set(false);
        }
        log.info("event=ai_analytics_backfill_completed runId={} status={} scanned={} recovered={} partial={} "
                        + "alreadyPresent={} skipped={} failed={}",
                run.getId(), run.getStatus(), run.getScanned(), run.getRecovered(), run.getPartiallyRecovered(),
                run.getAlreadyPresent(), run.getSkipped(), run.getFailed());
        return run;
    }

    public List<BackfillRun> recentRuns() {
        return mongoTemplate.find(new Query().with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(20), BackfillRun.class);
    }

    void importRequestJob(Document job, BackfillRun run) {
        run.setScanned(run.getScanned() + 1);
        String id = String.valueOf(job.get("_id"));
        try {
            if (store.findById(id).isPresent()) {
                run.setAlreadyPresent(run.getAlreadyPresent() + 1);
                return;
            }
            AiRequestAnalytics record = BackfillMapper.fromRequestJob(job, clock.instant());
            CorrelationResolver.Correlation correlation = correlationResolver.resolve(
                    record.getOperationContext(), record.getJobId(), record.getCorrelationId(), null);
            record.setRecipeId(correlation.recipeId());
            record.setProcessId(correlation.processId());
            record.setStepId(correlation.stepId());
            record.setWorkflowId(correlation.workflowId());
            record.setArtifactId(artifactIdFor(id));
            record.setOutputRef(BackfillMapper.historicalOutputRef(
                    record.getOperationContext(), record.getJobId(), record.getCorrelationId()));
            insert(record, run);
        } catch (RuntimeException e) {
            fail(run, id, e);
        }
    }

    void importChatResponse(Document response, BackfillRun run) {
        run.setScanned(run.getScanned() + 1);
        String id = BackfillMapper.chatRecordId(String.valueOf(response.get("_id")));
        try {
            if (store.findById(id).isPresent()) {
                run.setAlreadyPresent(run.getAlreadyPresent() + 1);
                return;
            }
            insert(BackfillMapper.fromChatResponse(response, clock.instant()), run);
        } catch (RuntimeException e) {
            fail(run, id, e);
        }
    }

    private void insert(AiRequestAnalytics record, BackfillRun run) {
        if (!store.insertIfAbsent(record)) {
            run.setAlreadyPresent(run.getAlreadyPresent() + 1);
            return;
        }
        boolean usage = record.getUsageTotals() != null
                && record.getUsageTotals().getUsageBasis() == UsageBasis.PROVIDER_REPORTED;
        boolean cost = record.getCostTotals() != null && record.getCostTotals().getCostBasis() != CostBasis.UNAVAILABLE;
        if (usage && cost) {
            run.setRecovered(run.getRecovered() + 1);
        } else {
            run.setPartiallyRecovered(run.getPartiallyRecovered() + 1);
        }
    }

    private String artifactIdFor(String requestId) {
        Query query = Query.query(where("producingJobId").is(requestId));
        query.fields().include("_id");
        Document artifact = mongoTemplate.findOne(query, Document.class, "ai_artifacts");
        return artifact == null ? null : String.valueOf(artifact.get("_id"));
    }

    private void fail(BackfillRun run, String id, RuntimeException e) {
        run.setFailed(run.getFailed() + 1);
        if (run.getFailures().size() < MAX_FAILURES_LISTED) {
            run.getFailures().add(id + ": " + e.getClass().getSimpleName());
        }
        log.warn("event=ai_analytics_backfill_record_failed id={} errorType={}", id, e.getClass().getSimpleName());
    }
}
