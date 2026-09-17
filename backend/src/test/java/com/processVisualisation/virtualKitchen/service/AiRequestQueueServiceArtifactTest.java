package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactHit;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactStatus;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.AiArtifactCodec;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.ai.credit.CreditReservation;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.queue.AiQueueProperties;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestJobRepository;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestProperties;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The billing test for the AI artifact store.
 * <p>
 * Credits are reserved in {@code ModelSelectionService.select}, which
 * {@code AiRequestQueueService.beginProcessing} calls before the {@code AiWork} lambda ever runs.
 * A reuse check placed anywhere downstream of that would still cost a credit on every hit, so these
 * tests pin the placement rather than merely the behaviour — deliberately using a <b>PAID</b> model,
 * since an OPEN_SOURCE one never reserves and would pass regardless.
 */
class AiRequestQueueServiceArtifactTest {

    private static final String MODEL_KEY = "test-paid-image";
    private static final String ARTIFACT_KEY = "TEXT_TO_IMAGE::visualization-image::recipe-1::step-1::abc";
    private static final int CREDIT_COST = 5;

    private CreditService creditService;
    private AiRequestJobRepository jobRepository;
    private AiArtifactService artifactService;
    private AiRequestQueueService queueService;
    private AiArtifactSpec<String> spec;

    /** Minimal codec: the payload type is irrelevant to the billing question under test. */
    private static final AiArtifactCodec<String> STRING_CODEC = new AiArtifactCodec<>() {
        @Override
        public String id() {
            return "test-string-v1";
        }

        @Override
        public AiArtifactPayload encode(String value) {
            return AiArtifactPayload.inline("text/plain", value);
        }

        @Override
        public String decode(AiArtifactPayload payload) {
            return payload.asText();
        }
    };

    @BeforeEach
    void setUp() {
        ModelDefinition paidImageModel = new ModelDefinition();
        paidImageModel.setKey(MODEL_KEY);
        paidImageModel.setCapability(AiCapability.TEXT_TO_IMAGE);
        paidImageModel.setTier(ModelTier.PAID);
        paidImageModel.setProviderBean("testImageClient");
        paidImageModel.setProviderModelId("test-image-model");
        paidImageModel.setCreditCost(CREDIT_COST);
        paidImageModel.setEnabled(true);

        AiModelProperties modelProperties = new AiModelProperties();
        modelProperties.setModels(List.of(paidImageModel));
        modelProperties.setDefaultModel(Map.of(AiCapability.TEXT_TO_IMAGE.name(), MODEL_KEY));
        AiModelRegistry modelRegistry = new AiModelRegistry(modelProperties);

        creditService = mock(CreditService.class);
        when(creditService.reserve(any(), anyInt(), any(), anyString()))
                .thenReturn(Optional.of(new CreditReservation("tx-1", CREDIT_COST, AiCapability.TEXT_TO_IMAGE, MODEL_KEY)));

        jobRepository = mock(AiRequestJobRepository.class);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        artifactService = mock(AiArtifactService.class);
        when(artifactService.findReusable(any(), any())).thenReturn(Optional.empty());

        queueService = new AiRequestQueueService(
                jobRepository,
                new ModelSelectionService(modelRegistry, creditService),
                creditService,
                new ThreadPoolTaskPool(2, "test-ai-text"),
                new AiQueueProperties(),
                new AiRequestProperties(),
                artifactService,
                modelRegistry);

        spec = AiArtifactSpec.of(ARTIFACT_KEY, STRING_CODEC, "test-consumer");
    }

    @Test
    void executeInline_artifactHit_spendsNoCreditsAndCallsNoProvider() {
        AiArtifact stored = storedArtifact();
        when(artifactService.findReusable(eq(7L), any()))
                .thenReturn(Optional.of(new AiArtifactHit<>(stored, "cached-payload")));
        AtomicInteger providerCalls = new AtomicInteger();

        AiRequestOutcome<String> outcome = queueService.executeInline(
                7L, AiCapability.TEXT_TO_IMAGE, null, null, "visualization-image", "recipe-1::step-1", spec,
                selection -> {
                    providerCalls.incrementAndGet();
                    return "fresh-payload";
                });

        assertEquals("cached-payload", outcome.value());
        assertTrue(outcome.reused());
        assertEquals(0, providerCalls.get(), "a hit must not call the provider");
        verify(creditService, never()).reserve(any(), anyInt(), any(), anyString());
        verify(creditService, never()).consume(any(), any(), anyString());
        verify(jobRepository, never()).save(any());
        assertNull(outcome.selection().reservation(),
                "a reused payload must carry no reservation, so nothing downstream can bill for it");
    }

    @Test
    void executeInline_artifactHit_reportsTheOriginallyBilledJobAndModel() {
        AiArtifact stored = storedArtifact();
        when(artifactService.findReusable(eq(7L), any()))
                .thenReturn(Optional.of(new AiArtifactHit<>(stored, "cached-payload")));

        AiRequestOutcome<String> outcome = queueService.executeInline(
                7L, AiCapability.TEXT_TO_IMAGE, null, null, "visualization-image", "recipe-1::step-1", spec,
                selection -> "fresh-payload");

        assertEquals("original-job-id", outcome.jobId(), "the audit trail must point at the paid call");
        assertEquals(MODEL_KEY, outcome.selection().model().getKey());
    }

    @Test
    void executeInline_artifactMiss_stagesThePayloadBeforeReturning() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();

        AiRequestOutcome<String> outcome = queueService.executeInline(
                7L, AiCapability.TEXT_TO_IMAGE, null, null, "visualization-image", "recipe-1::step-1", spec,
                selection -> {
                    providerCalls.incrementAndGet();
                    return "fresh-payload";
                });

        assertEquals("fresh-payload", outcome.value());
        assertFalse(outcome.reused());
        assertEquals(1, providerCalls.get());

        // The payload must be durable before the caller — and therefore any dependent work — sees it.
        InOrder order = inOrder(creditService, artifactService);
        order.verify(creditService).consume(any(), any(), anyString());
        order.verify(artifactService).stage(eq(7L), eq(AiCapability.TEXT_TO_IMAGE), eq("visualization-image"),
                eq("recipe-1::step-1"), any(), eq("fresh-payload"), any(), anyString());
    }

    @Test
    void executeInline_stagingFails_stillReturnsThePaidResult() throws Exception {
        when(artifactService.stage(any(), any(), anyString(), anyString(), any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("mongo unavailable"));

        AiRequestOutcome<String> outcome = queueService.executeInline(
                7L, AiCapability.TEXT_TO_IMAGE, null, null, "visualization-image", "recipe-1::step-1", spec,
                selection -> "fresh-payload");

        assertEquals("fresh-payload", outcome.value(),
                "failing a paid call to report a bookkeeping failure would be worse than not retaining it");
        assertNull(outcome.artifact());
    }

    @Test
    void executeInline_withoutASpec_leavesTheArtifactStoreUntouched() {
        AiRequestOutcome<String> outcome = queueService.executeInline(
                7L, AiCapability.TEXT_TO_IMAGE, null, null, "visualization-image", "recipe-1::step-1",
                selection -> "fresh-payload");

        assertEquals("fresh-payload", outcome.value());
        assertFalse(outcome.reused());
        verifyNoInteractions(artifactService);
        verify(creditService, times(1)).reserve(any(), anyInt(), any(), anyString());
    }

    private AiArtifact storedArtifact() {
        AiArtifact artifact = new AiArtifact();
        artifact.setId("artifact-1");
        artifact.setUserId(7L);
        artifact.setArtifactKey(ARTIFACT_KEY);
        artifact.setStatus(AiArtifactStatus.PENDING);
        artifact.setCapability(AiCapability.TEXT_TO_IMAGE);
        artifact.setCorrelationId("recipe-1::step-1");
        artifact.setProducedByModelKey(MODEL_KEY);
        artifact.setProducedByTier(ModelTier.PAID);
        artifact.setCreditCost(CREDIT_COST);
        artifact.setProducingJobId("original-job-id");
        return artifact;
    }
}
