package com.processVisualisation.virtualKitchen.ai.platform.analytics;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsTestSupport.Fixture;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifactHit;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.platform.artifact.codec.AiArtifactCodec;
import com.processVisualisation.virtualKitchen.ai.platform.credit.CreditReservation;
import com.processVisualisation.virtualKitchen.ai.platform.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.platform.dispatch.MeteredAiClient;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiQueueFullException;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiQueueProperties;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestJob;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestJobRepository;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestProperties;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.platform.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.ai.provider.AiClient;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiRequest;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiCommunicationException;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.common.logging.MdcKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsTestSupport.LOCAL_TEXT;
import static com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsTestSupport.PAID_TEXT;
import static com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsTestSupport.model;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The request queue records a persisted analytics record for every request it is given — successes, retries,
 * timeouts, rejections and artifact reuse — without changing its credit behaviour.
 */
class AiRequestQueueServiceAnalyticsTest {

    private Fixture fx;
    private CreditService creditService;
    private AiRequestJobRepository jobRepository;
    private AiArtifactService artifactService;
    private AiQueueProperties queueProperties;
    private AiRequestProperties requestProperties;
    private AiRequestQueueService queue;
    private final List<AiRequestJob> savedJobs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fx = AnalyticsTestSupport.fixture();
        ModelDefinition paid = model(PAID_TEXT, ModelTier.PAID, AiCapability.TEXT_TO_TEXT);
        paid.setFallbackModelKey(LOCAL_TEXT);
        ModelDefinition local = model(LOCAL_TEXT, ModelTier.OPEN_SOURCE, AiCapability.TEXT_TO_TEXT);
        AiModelProperties props = new AiModelProperties();
        props.setModels(List.of(paid, local));
        props.setDefaultModel(Map.of(AiCapability.TEXT_TO_TEXT.name(), PAID_TEXT));
        AiModelRegistry registry = new AiModelRegistry(props);

        creditService = mock(CreditService.class);
        when(creditService.reserve(any(), anyInt(), any(), anyString()))
                .thenReturn(Optional.of(new CreditReservation("tx-1", 1, AiCapability.TEXT_TO_TEXT, PAID_TEXT)));
        jobRepository = mock(AiRequestJobRepository.class);
        when(jobRepository.save(any())).thenAnswer(inv -> {
            savedJobs.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        artifactService = mock(AiArtifactService.class);
        when(artifactService.findReusable(any(), any())).thenReturn(Optional.empty());
        queueProperties = new AiQueueProperties();
        requestProperties = new AiRequestProperties();
        requestProperties.setRetryBackoffMs(1);

        queue = new AiRequestQueueService(jobRepository, new ModelSelectionService(registry, creditService), creditService,
                new ThreadPoolTaskPool(2, "test-ai-text"), queueProperties, requestProperties, artifactService,
                registry, fx.recorder());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** A provider stub behind the real metering wrapper, as {@code AiClientResolver} would hand out. */
    private static AiClient provider(java.util.function.Function<Integer, AiResponse> perCall) {
        AtomicInteger calls = new AtomicInteger();
        AiClient raw = request -> perCall.apply(calls.incrementAndGet());
        return new MeteredAiClient(raw, "gemini");
    }

    private static AiResponse response(String content) {
        return AiResponse.builder().content(content).model("gemini-flash-001")
                .promptTokens(1000).completionTokens(100).totalTokens(1100).build();
    }

    private static AiRequest request() {
        return AiRequest.builder().model("gemini-flash-id").userPrompt("p").operation("VISUALIZATION_PROMPT").build();
    }

    private AiRequestAnalytics onlyRecord() {
        assertThat(fx.store().size()).isEqualTo(1);
        return fx.store().get(savedJobs.isEmpty() ? null : savedJobs.get(0).getId());
    }

    @Test
    void success_createsOneRecordSharingTheJobId_withUsageCostAndCorrelation() {
        MDC.put(MdcKeys.JOB_ID, "viz-job-1");
        AiClient client = provider(n -> response("a prompt"));

        AiRequestOutcome<String> outcome = queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "recipe-5::step-2", selection -> client.chat(request()).getContent());

        AiRequestAnalytics record = fx.store().get(outcome.jobId());
        assertThat(record.getAiRequestJobId()).isEqualTo(outcome.jobId());
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
        assertThat(record.getResolvedModelKey()).isEqualTo(PAID_TEXT);
        assertThat(record.getCreditCost()).isEqualTo(1);
        assertThat(record.getAttempts()).singleElement().satisfies(a -> {
            assertThat(a.getPurpose()).isEqualTo("VISUALIZATION_PROMPT");
            assertThat(a.getTransportAttempt()).isEqualTo(1);
            assertThat(a.getUsage().getInputTokens()).isEqualTo(1000L);
            assertThat(a.getOutputId()).isNotNull();
        });
        assertThat(record.getTotalCost()).isNotNull();
        assertThat(record.getJobId()).isEqualTo("viz-job-1");
        assertThat(record.getRecipeId()).isEqualTo("recipe-5");
        assertThat(record.getStepId()).isEqualTo("step-2");
        assertThat(record.getQueueDepthAtSubmit()).isEqualTo(1);
        assertThat(record.getPoolName()).isEqualTo("visualization");
        verify(creditService).consume(eq(7L), any(), eq(outcome.jobId()), any());
    }

    @Test
    void transportRetry_recordsBothAttemptsAndConsumesCreditsOnce() {
        AiClient client = provider(n -> {
            if (n == 1) throw new AiCommunicationException("503 from provider");
            return response("ok");
        });

        AiRequestOutcome<String> outcome = queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "r::s", selection -> client.chat(request()).getContent());

        AiRequestAnalytics record = fx.store().get(outcome.jobId());
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.SUCCEEDED);
        assertThat(record.getTransportAttempts()).isEqualTo(2);
        assertThat(record.getAttempts()).extracting(AiRequestAnalytics.Attempt::getStatus)
                .containsExactly(AnalyticsStatus.FAILED, AnalyticsStatus.SUCCEEDED);
        assertThat(record.getAttempts()).extracting(AiRequestAnalytics.Attempt::getTransportAttempt).containsExactly(1, 2);
        assertThat(record.getAttempts().get(0).getErrorCategory()).isEqualTo("PROVIDER_ERROR");
        assertThat(record.getCostTotals().getComplete()).as("the failed call's charge is unknown").isFalse();
        verify(creditService, times(1)).consume(any(), any(), any(), any());
        verify(creditService, never()).release(any(), any(), any(), any());
    }

    @Test
    void finalFailure_isRecordedAndCreditsReleased() {
        AiClient client = provider(n -> {
            throw new AiCommunicationException("down");
        });

        assertThatThrownBy(() -> queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "r::s", selection -> client.chat(request()).getContent()))
                .isInstanceOf(AiCommunicationException.class);

        AiRequestAnalytics record = onlyRecord();
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.FAILED);
        assertThat(record.getErrorCategory()).isEqualTo("PROVIDER_ERROR");
        assertThat(record.getAttempts()).hasSize(2);
        verify(creditService).release(any(), any(), any(), any());
    }

    @Test
    void timeout_isRecordedAsTimedOut_andTheLateCallIsStillCaptured() throws Exception {
        requestProperties.setTimeoutMs(Map.of(AiCapability.TEXT_TO_TEXT.name(), 100L));
        requestProperties.setMaxRetries(0);
        AiClient client = provider(n -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return response("late");
        });

        assertThatThrownBy(() -> queue.executeBounded(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-generation", null, selection -> client.chat(request()).getContent()))
                .isInstanceOf(RuntimeException.class);

        String id = savedJobs.get(0).getId();
        assertThat(fx.store().get(id).getStatus()).isEqualTo(AnalyticsStatus.TIMED_OUT);
        assertThat(fx.store().get(id).getTimeoutMs()).isEqualTo(100L);

        long deadline = System.currentTimeMillis() + 5000;
        while (fx.store().get(id).getAttempts().isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        AiRequestAnalytics record = fx.store().get(id);
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.TIMED_OUT);
        assertThat(record.getAttempts()).singleElement().satisfies(a -> assertThat(a.isLate()).isTrue());
        assertThat(record.getUsageTotals().getInputTokens()).isEqualTo(1000L);
    }

    @Test
    void queueFull_isRecordedAsRejected() {
        queueProperties.setAiImageMaxDepth(0);

        assertThatThrownBy(() -> queue.executeInline(7L, AiCapability.TEXT_TO_IMAGE, null, null,
                "process-visualization-image", "r::s", selection -> "never"))
                .isInstanceOf(AiQueueFullException.class);

        assertThat(fx.store().size()).isEqualTo(1);
        AiRequestAnalytics record = fx.store().findById(firstId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.REJECTED);
        assertThat(record.getErrorCategory()).isEqualTo("QUEUE_FULL");
        assertThat(record.getAttempts()).isEmpty();
        assertThat(savedJobs).isEmpty();
    }

    @Test
    void creditFallback_recordsTheModelThatActuallyRan() {
        when(creditService.reserve(any(), anyInt(), any(), anyString())).thenReturn(Optional.empty());
        AiClient client = provider(n -> response("ok"));

        AiRequestOutcome<String> outcome = queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "r::s", selection -> client.chat(request()).getContent());

        AiRequestAnalytics record = fx.store().get(outcome.jobId());
        assertThat(record.getResolvedModelKey()).isEqualTo(LOCAL_TEXT);
        assertThat(record.getUsedFallback()).isTrue();
        assertThat(record.getFallbackReason()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(record.getModelTier()).isEqualTo("OPEN_SOURCE");
        assertThat(record.getCostTotals().getDirectCost()).isZero();
    }

    @Test
    void artifactReuse_isRecordedAsReused_withTheProducingRequest() {
        AiArtifact stored = new AiArtifact();
        stored.setId("artifact-1");
        stored.setProducingJobId("original-job");
        stored.setProducedByModelKey(PAID_TEXT);
        stored.setProducedByTier(ModelTier.PAID);
        stored.setCapability(AiCapability.TEXT_TO_TEXT);
        when(artifactService.findReusable(eq(7L), any())).thenReturn(Optional.of(new AiArtifactHit<>(stored, "cached")));

        AiRequestOutcome<String> outcome = queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "r::s", spec(), selection -> "fresh");

        assertThat(outcome.reused()).isTrue();
        AiRequestAnalytics record = fx.store().findById(firstId()).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(AnalyticsStatus.REUSED);
        assertThat(record.getReusedFromRequestId()).isEqualTo("original-job");
        assertThat(record.getArtifactId()).isEqualTo("artifact-1");
        verify(creditService, never()).reserve(any(), anyInt(), any(), anyString());
    }

    @Test
    void stagedArtifact_isLinkedToItsRequest() throws Exception {
        AiArtifact staged = new AiArtifact();
        staged.setId("artifact-9");
        when(artifactService.stage(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(staged);

        AiRequestOutcome<String> outcome = queue.executeInline(7L, AiCapability.TEXT_TO_TEXT, null, null,
                "process-visualization-prompt", "r::s", spec(), selection -> "fresh");

        assertThat(fx.store().get(outcome.jobId()).getArtifactId()).isEqualTo("artifact-9");
    }

    /** The only record's id (for paths where no job is saved). */
    private String firstId() {
        return fx.store().onlyId();
    }

    private static AiArtifactSpec<String> spec() {
        return AiArtifactSpec.of("key", new AiArtifactCodec<>() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public AiArtifactPayload encode(String value) {
                return AiArtifactPayload.inline("text/plain", value);
            }

            @Override
            public String decode(AiArtifactPayload payload) throws Exception {
                return payload.asText();
            }
        }, "consumer");
    }
}
