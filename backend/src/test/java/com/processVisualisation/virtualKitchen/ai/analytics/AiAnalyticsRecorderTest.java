package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.Fixture;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsRequestType;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsSource;
import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.analytics.model.DataCompleteness;
import com.processVisualisation.virtualKitchen.ai.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.analytics.model.UsageBasis;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.LOCAL_TEXT;
import static com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.PAID_TEXT;
import static com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.failedCall;
import static com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.model;
import static com.processVisualisation.virtualKitchen.ai.analytics.AnalyticsTestSupport.textCall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AiAnalyticsRecorderTest {

    private final Fixture fx = AnalyticsTestSupport.fixture();
    private final AiAnalyticsRecorder recorder = fx.recorder();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String begin(String correlationType, String correlationId) {
        return recorder.begin(new AiAnalyticsRecorder.Begin(null, 7L, AiCapability.TEXT_TO_TEXT, null,
                correlationType, correlationId, "idem-1", null, "ai-text", 30_000L));
    }

    private AiAnalyticsRecorder.RequestTracker resolvedTracker(String id, String modelKey, ModelTier tier) {
        recorder.modelResolved(id, model(modelKey, tier, AiCapability.TEXT_TO_TEXT), false, "NONE", 1, "tx-1", id, 3);
        AiAnalyticsRecorder.RequestTracker tracker = recorder.tracker(id, OperationContext.RECIPE_FLOW_GENERATION);
        tracker.model(model(modelKey, tier, AiCapability.TEXT_TO_TEXT));
        return tracker;
    }

    @Test
    void begin_persistsAQueuedRecordBeforeAnythingRuns_withCorrelationFromMdc() {
        MDC.put(MdcKeys.JOB_ID, "job-9");
        MDC.put(AiAnalyticsRecorder.WORKFLOW_ID_MDC, "wf-3");

        String id = begin("process-visualization-image", "recipe-12::step-4");

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getSource()).isEqualTo(AnalyticsSource.LIVE);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.QUEUED);
        assertThat(record.getRequestType()).isEqualTo(AnalyticsRequestType.TEXT_TO_TEXT);
        assertThat(record.getOperationContext()).isEqualTo(OperationContext.STEP_IMAGE_GENERATION);
        assertThat(record.getJobId()).isEqualTo("job-9");
        assertThat(record.getWorkflowId()).isEqualTo("wf-3");
        assertThat(record.getRecipeId()).isEqualTo("recipe-12");
        assertThat(record.getStepId()).isEqualTo("step-4");
        assertThat(record.getQueuedAt()).isNotNull();
        assertThat(record.getUserId()).isEqualTo(7L);
    }

    @Test
    void successfulRequest_persistsUsageCostStatusTimingAndOutputLink() {
        String id = begin("process-generation", null);
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);
        tracker.markWorkStarted();

        tracker.onCall(textCall("RECIPE_TO_FLOW", 1_000_000, 200_000, "{\"mainProcess\":{}}"));
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, tracker.workStartedAt());

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
        assertThat(record.getResolvedModelKey()).isEqualTo(PAID_TEXT);
        assertThat(record.getFinalProviderModelId()).as("actual model reported by the provider").isEqualTo("gemini-flash-001");
        assertThat(record.getAttempts()).hasSize(1);
        assertThat(record.getAttemptCount()).isEqualTo(1);
        assertThat(record.getTransportAttempts()).isEqualTo(1);
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(1_000_000L);
        assertThat(record.getUsageTotals().getUsageBasis()).isEqualTo(UsageBasis.PROVIDER_REPORTED);
        assertThat(record.getCostTotals().getCostBasis()).isEqualTo(CostBasis.CALCULATED);
        assertThat(record.getTotalCost()).isCloseTo(0.55, within(1e-9));
        assertThat(record.getCostTotals().getPricingVersion()).isEqualTo("v1");
        assertThat(record.getCompletedAt()).isNotNull();
        assertThat(record.getTotalMs()).isNotNull();
        assertThat(record.getQueueWaitMs()).isNotNull();
        assertThat(record.getExecutionMs()).isNotNull();
        assertThat(record.getQueueDepthAtSubmit()).isEqualTo(3);
        assertThat(record.getDataCompleteness()).isEqualTo(DataCompleteness.COMPLETE);
        assertThat(record.getOutputRef().getCollection()).isEqualTo("ai_responses");
        assertThat(fx.sink().saved).singleElement().satisfies(saved -> {
            assertThat(saved.requestId()).isEqualTo(id);
            assertThat(saved.output()).isEqualTo("{\"mainProcess\":{}}");
        });
        assertThat(record.getStatusHistory()).extracting(AiRequestAnalytics.StatusChange::getStatus)
                .containsExactly(AnalyticsStatus.QUEUED, AnalyticsStatus.RUNNING, AnalyticsStatus.SUCCEEDED);
    }

    @Test
    void failedRequest_persistsStatusCategoryAndASanitizedError() {
        String id = begin("process-generation", null);
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);

        tracker.onCall(failedCall("AICommunicationException", 429,
                "Rate limited calling https://api.example/v1/models?key=AIzaSyDUMMYDUMMYDUMMYDUMMY12345 Bearer abc.def"));
        recorder.fail(id, new RuntimeException("boom api_key=secret-value"), 2, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.FAILED);
        assertThat(record.getErrorMessage()).doesNotContain("secret-value").contains("[redacted]");
        AiRequestAnalytics.Attempt attempt = record.getAttempts().get(0);
        assertThat(attempt.getStatus()).isEqualTo(AnalyticsStatus.FAILED);
        assertThat(attempt.getErrorCategory()).isEqualTo("RATE_LIMITED");
        assertThat(attempt.getErrorMessage()).doesNotContain("AIzaSy").doesNotContain("abc.def").doesNotContain("key=");
        assertThat(attempt.getCost().getCostBasis()).isEqualTo(CostBasis.UNAVAILABLE);
        assertThat(record.getCostTotals().getComplete()).isFalse();
        assertThat(record.getTotalCost()).as("missing cost is never zero").isNull();
    }

    @Test
    void retries_recordEveryBillableAttemptAndSumTheirCost() {
        String id = begin("process-generation", null);
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);

        tracker.onCall(textCall("RECIPE_TO_FLOW", 1_000_000, 0, "not json"));
        tracker.onValidation(false, List.of("Invalid JSON"));
        tracker.onCall(textCall("RECIPE_TO_FLOW_RETRY", 1_000_000, 0, "{}"));
        tracker.onValidation(true, List.of());
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getAttempts()).extracting(AiRequestAnalytics.Attempt::getPurpose)
                .containsExactly("RECIPE_TO_FLOW", "RECIPE_TO_FLOW_RETRY");
        assertThat(record.getAttempts()).extracting(AiRequestAnalytics.Attempt::getValidationPassed)
                .containsExactly(false, true);
        assertThat(record.getTotalCost()).isCloseTo(0.50, within(1e-9));
        assertThat(record.getValidationPassed()).isTrue();
        assertThat(record.getOutputRef().getId()).as("the accepted output, not the first")
                .isEqualTo(record.getAttempts().get(1).getOutputId());
    }

    @Test
    void duplicateCompletion_doesNotChangeTheOutcome() {
        String id = begin("process-generation", null);
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);
        recorder.fail(id, new AITimeoutException("late timeout"), 2, null);
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
        assertThat(record.getErrorMessage()).isNull();
        assertThat(record.getStatusHistory()).hasSize(2);
    }

    @Test
    void timeout_isRecordedAndALateAttemptIsStillCounted() {
        String id = begin("process-generation", null);
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);

        recorder.fail(id, new java.util.concurrent.TimeoutException(), 1, null);
        tracker.onCall(textCall("RECIPE_TO_FLOW", 1000, 1000, "{}"));

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.TIMED_OUT);
        assertThat(record.getErrorCategory()).isEqualTo("TIMEOUT");
        assertThat(record.getAttempts()).singleElement().satisfies(a -> assertThat(a.isLate()).isTrue());
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(1000L);
        assertThat(record.getTotalCost()).isNotNull();
    }

    @Test
    void missingProviderUsage_staysUnavailable() {
        String id = begin("process-visualization-image", "r::s");
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);

        tracker.onCall(AiCallRecord.builder().purpose("IMAGE_GENERATION").provider("gemini").success(true)
                .durationMs(10).outputBytes(1000L).build());
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getUsageTotals().getInputTokens()).isNull();
        assertThat(record.getUsageTotals().getUsageBasis()).isEqualTo(UsageBasis.NOT_REPORTED);
        assertThat(record.getTotalCost()).isNull();
        assertThat(record.getDataCompleteness()).isEqualTo(DataCompleteness.PARTIAL);
        assertThat(record.getMissingFields()).contains("usage", "cost");
    }

    @Test
    void localModel_hasCalculatedZeroDirectCost() {
        String id = begin("process-generation", null);
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, LOCAL_TEXT, ModelTier.OPEN_SOURCE);

        tracker.onCall(textCall("RECIPE_TO_FLOW", 500, 500, "{}"));
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getCostTotals().getDirectCost()).isZero();
        assertThat(record.getCostTotals().getInfraCostBasis()).isEqualTo(CostBasis.UNAVAILABLE);
        assertThat(record.getModelTier()).isEqualTo("OPEN_SOURCE");
    }

    @Test
    void writeFailure_isCountedQueuedAndAppliedOnRetry() {
        String id = begin("process-generation", null);
        fx.store().failNextWrites.set(1);

        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);

        assertThat(fx.health().snapshot(recorder.pendingWrites()).writeFailures()).isEqualTo(1);
        assertThat(recorder.pendingWrites()).isEqualTo(1);
        assertThat(fx.store().get(id).getStatus()).isEqualTo(AnalyticsStatus.QUEUED);

        recorder.retryPendingWrites();

        assertThat(recorder.pendingWrites()).isZero();
        assertThat(fx.health().snapshot(0).retriedOk()).isEqualTo(1);
        assertThat(fx.store().get(id).getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
    }

    @Test
    void beginWriteFailure_isRetriedBeforeLaterUpdates() {
        fx.store().failNextWrites.set(1);
        String id = begin("process-generation", null);
        recorder.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);
        assertThat(recorder.pendingWrites()).isEqualTo(2);

        recorder.retryPendingWrites();

        assertThat(fx.store().get(id).getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
    }

    @Test
    void concurrentAttempts_areAllKeptAndSummedCorrectly() throws Exception {
        String id = begin("process-visualization-prompt", "r::s");
        AiAnalyticsRecorder.RequestTracker tracker = resolvedTracker(id, PAID_TEXT, ModelTier.PAID);
        int calls = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < calls; i++) {
            pool.submit(() -> {
                start.await();
                tracker.onCall(textCall("VISUALIZATION_PROMPT", 100, 10, "prompt"));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getAttempts()).hasSize(calls);
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(100L * calls);
        assertThat(record.getAttempts()).extracting(AiRequestAnalytics.Attempt::getCallIndex).doesNotHaveDuplicates();
    }

    @Test
    void outputStored_keepsTheFirstUrlForARequest() {
        String id = begin("process-visualization-image", "r::s");

        recorder.outputStored(id, "IMAGE", "visualization_asset", "42", "https://cdn/first.png", "image/png", 10L);
        recorder.outputStored(id, "IMAGE", "visualization_asset", "42", "https://cdn/second.png", "image/png", 10L);

        assertThat(fx.store().get(id).getOutputRef().getUrl()).isEqualTo("https://cdn/first.png");
    }

    @Test
    void reused_isRecordedWithoutAttemptsOrCost() {
        String id = begin("process-visualization-image", "r::s");

        recorder.reused(id, "artifact-1", "original-request", model(PAID_TEXT, ModelTier.PAID, AiCapability.TEXT_TO_IMAGE), false);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.REUSED);
        assertThat(record.getReusedFromRequestId()).isEqualTo("original-request");
        assertThat(record.getArtifactId()).isEqualTo("artifact-1");
        assertThat(record.getAttempts()).isEmpty();
        assertThat(record.getTotalCost()).isNull();
        assertThat(record.getDataCompleteness()).isEqualTo(DataCompleteness.COMPLETE);
    }

    @Test
    void reconcileStale_closesAStuckRecordWithoutInventingTimings() {
        String id = begin("process-generation", null);

        recorder.reconcileStale(id, null, null);

        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.FAILED);
        assertThat(record.getErrorCategory()).isEqualTo("ABANDONED");
        assertThat(record.getMissingFields()).contains("completion");
        assertThat(record.getCompletedAt()).isNull();
        assertThat(record.getTotalMs()).isNull();
    }

    @Test
    void disabledRecorder_recordsNothingAndNeverThrows() {
        AiAnalyticsRecorder disabled = AiAnalyticsRecorder.disabled();
        String id = disabled.begin(new AiAnalyticsRecorder.Begin(null, 1L, AiCapability.TEXT_TO_TEXT, null,
                "chat", null, null, null, null, null));
        disabled.tracker(id, OperationContext.CHAT).onCall(textCall("CHAT", 1, 1, "hi"));
        disabled.complete(id, AnalyticsStatus.SUCCEEDED, null, 1, null);
        assertThat(disabled.pendingWrites()).isZero();
    }
}
