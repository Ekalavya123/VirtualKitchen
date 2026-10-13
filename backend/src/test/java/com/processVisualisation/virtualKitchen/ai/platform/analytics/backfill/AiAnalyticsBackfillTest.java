package com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.CorrelationResolver;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.InMemoryAnalyticsStore;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsRequestType;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsSource;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.DataCompleteness;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.UsageBasis;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiAnalyticsBackfillTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant QUEUED = Instant.parse("2026-09-01T10:00:00Z");

    private InMemoryAnalyticsStore store;
    private MongoTemplate mongo;
    private AiAnalyticsBackfillService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryAnalyticsStore();
        mongo = mock(MongoTemplate.class);
        // The generation job and the workflow that started it, as they exist in the database.
        when(mongo.findById(eq("gen-job-1"), eq(Document.class), eq("process_generation_job")))
                .thenReturn(new Document("_id", "gen-job-1").append("recipeId", 55L).append("userId", 7L));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("recipe_ai_workflow")))
                .thenReturn(new Document("_id", "wf-1"));
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("ai_artifacts"))).thenReturn(null);
        service = new AiAnalyticsBackfillService(mongo, store, new CorrelationResolver(mongo),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static Document job(String id, String status, Document usage) {
        Document job = new Document("_id", id)
                .append("userId", 7L)
                .append("capability", "TEXT_TO_TEXT")
                .append("resolvedModelKey", "gemini-flash")
                .append("resolvedTier", "PAID")
                .append("usedFallback", false)
                .append("creditCost", 1)
                .append("status", status)
                .append("attempt", 1)
                .append("correlationType", "process-generation")
                .append("operationId", "gen-job-1")
                .append("queuedAt", Date.from(QUEUED))
                .append("startedAt", Date.from(QUEUED.plusMillis(250)))
                .append("completedAt", Date.from(QUEUED.plusMillis(4250)))
                .append("durationMs", 4000L);
        if (usage != null) job.append("usage", usage);
        return job;
    }

    static Document usage(long prompt, long completion, Double cost) {
        return new Document("providerCalls", 2).append("promptTokens", prompt).append("cachedTokens", 0L)
                .append("completionTokens", completion).append("thoughtsTokens", 0L)
                .append("totalTokens", prompt + completion).append("estimatedCostUsd", cost);
    }

    @Test
    void recoverableUsage_isImportedAsHistoricalWithALegacyEstimate() {
        BackfillRun run = new BackfillRun();
        service.importRequestJob(job("job-1", "COMPLETED", usage(12_000, 800, 0.0042)), run);

        AiRequestAnalytics record = store.get("job-1");
        assertThat(record.getSource()).isEqualTo(AnalyticsSource.BACKFILL_REQUEST_JOB);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
        assertThat(record.getRequestType()).isEqualTo(AnalyticsRequestType.TEXT_TO_TEXT);
        assertThat(record.getOperationContext()).isEqualTo(OperationContext.RECIPE_FLOW_GENERATION);
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(12_000L);
        assertThat(record.getUsageTotals().getUsageBasis()).isEqualTo(UsageBasis.PROVIDER_REPORTED);
        assertThat(record.getAttemptCount()).isEqualTo(2);
        assertThat(record.getCostTotals().getCostBasis()).as("never presented as actual").isEqualTo(CostBasis.ESTIMATED);
        assertThat(record.getCostTotals().getPricingVersion()).isEqualTo(BackfillMapper.LEGACY_PRICING_VERSION);
        assertThat(record.getTotalCost()).isEqualTo(0.0042);
        assertThat(record.getQueueWaitMs()).isEqualTo(250L);
        assertThat(record.getExecutionMs()).isEqualTo(4000L);
        assertThat(record.getTotalMs()).isEqualTo(4250L);
        assertThat(record.getAttempts()).as("no per-attempt detail was ever recorded").isEmpty();
        assertThat(record.getMissingFields()).contains("attempts", "provider").doesNotContain("usage", "cost");
        assertThat(record.getDataCompleteness()).isEqualTo(DataCompleteness.HISTORICAL_PARTIAL);
        assertThat(run.getRecovered()).isEqualTo(1);
    }

    @Test
    void historicalJob_isCorrelatedWithItsRecipeWorkflowAndResult() {
        service.importRequestJob(job("job-1", "COMPLETED", usage(10, 10, 0.001)), new BackfillRun());

        AiRequestAnalytics record = store.get("job-1");
        assertThat(record.getJobId()).isEqualTo("gen-job-1");
        assertThat(record.getRecipeId()).isEqualTo("55");
        assertThat(record.getWorkflowId()).isEqualTo("wf-1");
        assertThat(record.getOutputRef().getCollection()).isEqualTo("process_generation_job");
        assertThat(record.getOutputRef().getId()).isEqualTo("gen-job-1");
    }

    @Test
    void modelAndStatusWithoutUsage_showsUsageAndCostAsUnavailable() {
        BackfillRun run = new BackfillRun();
        service.importRequestJob(job("job-2", "FAILED", null), run);

        AiRequestAnalytics record = store.get("job-2");
        assertThat(record.getResolvedModelKey()).isEqualTo("gemini-flash");
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.FAILED);
        assertThat(record.getUsageTotals()).isNull();
        assertThat(record.getTotalCost()).isNull();
        assertThat(record.getCostTotals().getCostBasis()).isEqualTo(CostBasis.UNAVAILABLE);
        assertThat(record.getMissingFields()).contains("usage", "cost");
        assertThat(run.getPartiallyRecovered()).isEqualTo(1);
    }

    @Test
    void allZeroLegacyUsage_isNotReported_andItsZeroCostIsNotTrusted() {
        service.importRequestJob(job("job-3", "COMPLETED", usage(0, 0, 0.0)), new BackfillRun());

        AiRequestAnalytics record = store.get("job-3");
        assertThat(record.getUsageTotals().getUsageBasis()).isEqualTo(UsageBasis.NOT_REPORTED);
        assertThat(record.getUsageTotals().getInputTokens()).isNull();
        assertThat(record.getTotalCost()).isNull();
        assertThat(record.getCostTotals().getCostBasis()).isEqualTo(CostBasis.UNAVAILABLE);
    }

    @Test
    void missingLatency_staysUnavailable() {
        Document job = job("job-4", "PROCESSING", null);
        job.remove("completedAt");
        job.remove("durationMs");

        service.importRequestJob(job, new BackfillRun());

        AiRequestAnalytics record = store.get("job-4");
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.RUNNING);
        assertThat(record.getExecutionMs()).isNull();
        assertThat(record.getTotalMs()).isNull();
        assertThat(record.getMissingFields()).contains("latency");
    }

    @Test
    void localEngine_hasACalculatedZeroDirectCost() {
        Document job = job("job-5", "COMPLETED", usage(0, 0, 0.0)).append("resolvedTier", "OPEN_SOURCE");

        service.importRequestJob(job, new BackfillRun());

        assertThat(store.get("job-5").getCostTotals().getCostBasis()).isEqualTo(CostBasis.CALCULATED);
        assertThat(store.get("job-5").getTotalCost()).isZero();
    }

    @Test
    void rerun_neverDuplicatesOrOverwrites_liveRecordsIncluded() {
        AiRequestAnalytics live = new AiRequestAnalytics();
        live.setId("job-live");
        live.setSource(AnalyticsSource.LIVE);
        live.setStatus(AnalyticsStatus.SUCCEEDED);
        store.insertIfAbsent(live);

        BackfillRun first = new BackfillRun();
        service.importRequestJob(job("job-1", "COMPLETED", usage(10, 10, 0.001)), first);
        service.importRequestJob(job("job-live", "COMPLETED", usage(10, 10, 0.001)), first);
        BackfillRun second = new BackfillRun();
        service.importRequestJob(job("job-1", "COMPLETED", usage(99, 99, 9.0)), second);

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.get("job-1").getTotalCost()).isEqualTo(0.001);
        assertThat(store.get("job-live").getSource()).isEqualTo(AnalyticsSource.LIVE);
        assertThat(first.getRecovered()).isEqualTo(1);
        assertThat(first.getAlreadyPresent()).isEqualTo(1);
        assertThat(second.getAlreadyPresent()).isEqualTo(1);
        assertThat(second.getRecovered()).isZero();
    }

    @Test
    void stepScopedJob_isLinkedToItsArtifactAndAsset() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("ai_artifacts")))
                .thenReturn(new Document("_id", "artifact-3"));
        Document job = job("job-6", "COMPLETED", usage(20, 1290, 0.0387))
                .append("capability", "TEXT_TO_IMAGE")
                .append("correlationType", "process-visualization-image")
                .append("correlationId", "55::step-9")
                .append("operationId", "viz-job-1");

        service.importRequestJob(job, new BackfillRun());

        AiRequestAnalytics record = store.get("job-6");
        assertThat(record.getRequestType()).isEqualTo(AnalyticsRequestType.TEXT_TO_IMAGE);
        assertThat(record.getRecipeId()).isEqualTo("55");
        assertThat(record.getStepId()).isEqualTo("step-9");
        assertThat(record.getArtifactId()).isEqualTo("artifact-3");
        assertThat(record.getOutputRef().getKind()).isEqualTo("IMAGE");
        assertThat(record.getOutputRef().getId()).isEqualTo("55::step-9");
    }

    @Test
    void chatResponse_isImportedWithItsTimestampTokensAndOutput_butNoCost() {
        ObjectId objectId = new ObjectId(Date.from(QUEUED));
        Document response = new Document("_id", objectId)
                .append("context", "chat")
                .append("success", true)
                .append("responseData", new Document("content", "Hello!").append("model", "gemini-3.1-flash-lite")
                        .append("promptTokens", 30).append("completionTokens", 5).append("totalTokens", 35));

        BackfillRun run = new BackfillRun();
        service.importChatResponse(response, run);

        AiRequestAnalytics record = store.get(BackfillMapper.chatRecordId(objectId.toHexString()));
        assertThat(record.getSource()).isEqualTo(AnalyticsSource.BACKFILL_AI_RESPONSE);
        assertThat(record.getOperationContext()).isEqualTo(OperationContext.CHAT);
        assertThat(record.getCreatedAt()).isEqualTo(QUEUED);
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(30L);
        assertThat(record.getFinalProviderModelId()).isEqualTo("gemini-3.1-flash-lite");
        assertThat(record.getCostTotals().getCostBasis()).isEqualTo(CostBasis.UNAVAILABLE);
        assertThat(record.getExecutionMs()).isNull();
        assertThat(record.getOutputRef().getCollection()).isEqualTo("ai_responses");
        assertThat(run.getPartiallyRecovered()).isEqualTo(1);
    }

    @Test
    void historicalOutputRef_isUnavailableWithoutAReliableLink() {
        assertThat(BackfillMapper.historicalOutputRef(OperationContext.RECIPE_FLOW_GENERATION, null, null)).isNull();
        assertThat(BackfillMapper.historicalOutputRef(OperationContext.UNKNOWN, "x", "y")).isNull();
        assertThat(BackfillMapper.historicalOutputRef(OperationContext.STEP_NARRATION, null, "55::s1|token-1").getId())
                .isEqualTo("55::s1");
    }
}
