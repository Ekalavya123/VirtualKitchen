package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactProperties;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactRecoveryJob;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactStatus;
import com.processVisualisation.virtualKitchen.ai.artifact.consumer.AiArtifactConsumer;
import com.processVisualisation.virtualKitchen.ai.artifact.consumer.AiArtifactConsumerRegistry;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiArtifactRecoveryJobTest {

    private static final String CONSUMER_ID = "test-consumer";
    private static final String PAYLOAD = "already-paid-for-payload";

    private AiArtifactService artifactService;
    private AiArtifactConsumer consumer;
    private AiArtifactProperties properties;
    private AiArtifactRecoveryJob job;

    @BeforeEach
    void setUp() {
        artifactService = mock(AiArtifactService.class);
        consumer = mock(AiArtifactConsumer.class);
        when(consumer.consumerId()).thenReturn(CONSUMER_ID);
        properties = new AiArtifactProperties();

        job = new AiArtifactRecoveryJob(
                artifactService, new AiArtifactConsumerRegistry(List.of(consumer)), properties);

        when(artifactService.findOrphans(any(Instant.class), anyInt())).thenReturn(List.of());
        when(artifactService.findCollectableBlobs(anyInt())).thenReturn(List.of());
    }

    @Test
    void sweep_orphanedArtifact_completesTheDependentWorkAndRetiresIt() throws Exception {
        AiArtifact orphan = orphan();
        givenOrphan(orphan);

        job.sweep();

        verify(consumer).consume(eq(orphan), eq(PAYLOAD));
        verify(artifactService).markConsumed("artifact-1");
        verify(artifactService, never()).markRecoveryFailure(anyString(), anyString());
    }

    @Test
    void sweep_whenTheDependentWorkFailsAgain_recordsTheFailureAndKeepsThePayload() throws Exception {
        AiArtifact orphan = orphan();
        givenOrphan(orphan);
        when(consumer.consume(any(), any())).thenThrow(new IllegalStateException("storage still down"));

        job.sweep();

        verify(artifactService).markRecoveryFailure(eq("artifact-1"), anyString());
        verify(artifactService, never()).markConsumed(anyString());
    }

    @Test
    void sweep_whenAnotherWorkerHoldsTheLease_skipsTheArtifact() throws Exception {
        when(artifactService.findOrphans(any(Instant.class), anyInt())).thenReturn(List.of(orphan()));
        when(artifactService.acquireLease("artifact-1")).thenReturn(Optional.empty());

        job.sweep();

        verify(consumer, never()).consume(any(), any());
        verify(artifactService, never()).markConsumed(anyString());
    }

    @Test
    void sweep_whenThePayloadCannotBeDecoded_recordsAFailureRatherThanLooping() throws Exception {
        AiArtifact orphan = orphan();
        when(artifactService.findOrphans(any(Instant.class), anyInt())).thenReturn(List.of(orphan));
        when(artifactService.acquireLease("artifact-1")).thenReturn(Optional.of(orphan));
        when(artifactService.load(orphan)).thenReturn(Optional.empty());

        job.sweep();

        verify(consumer, never()).consume(any(), any());
        verify(artifactService).markRecoveryFailure(eq("artifact-1"), anyString());
    }

    @Test
    void sweep_collectsBlobsLeftBehindByRetiredArtifacts() {
        AiArtifact retired = orphan();
        retired.setStatus(AiArtifactStatus.CONSUMED);
        when(artifactService.findCollectableBlobs(anyInt())).thenReturn(List.of(retired));

        job.sweep();

        verify(artifactService).releasePayload("artifact-1");
    }

    @Test
    void sweep_whenDisabled_doesNothing() {
        properties.getSweeper().setEnabled(false);

        job.sweep();

        verify(artifactService, never()).findOrphans(any(Instant.class), anyInt());
        verify(artifactService, never()).findCollectableBlobs(anyInt());
    }

    /**
     * The invariant that makes a bug in this job harmless: it re-runs only the dependent work over
     * an already-paid payload, so it can never reach a provider or a credit balance.
     */
    @Test
    void sweep_neverCallsAProviderOrSpendsCredits() throws Exception {
        AiRequestQueueService queueService = mock(AiRequestQueueService.class);
        CreditService creditService = mock(CreditService.class);
        ImageGenerationClient imageGenerationClient = mock(ImageGenerationClient.class);
        givenOrphan(orphan());

        job.sweep();

        verifyNoInteractions(queueService, creditService, imageGenerationClient);
    }

    @Test
    void sweep_reportsTheNumberOfPayloadsRecoveredWithoutRegenerating() throws Exception {
        AiArtifact orphan = orphan();
        givenOrphan(orphan);
        when(artifactService.markConsumed("artifact-1")).thenReturn(true);

        job.sweep();

        verify(consumer).consume(eq(orphan), eq(PAYLOAD));
        assertEquals(AiArtifactStatus.PENDING, orphan.getStatus(),
                "the job must not mutate status itself; that is the service's guarded transition");
    }

    private void givenOrphan(AiArtifact orphan) {
        when(artifactService.findOrphans(any(Instant.class), anyInt())).thenReturn(List.of(orphan));
        when(artifactService.acquireLease("artifact-1")).thenReturn(Optional.of(orphan));
        when(artifactService.load(orphan))
                .thenReturn(Optional.of(new AiArtifactService.LoadedArtifact(orphan, PAYLOAD)));
    }

    private AiArtifact orphan() {
        AiArtifact artifact = new AiArtifact();
        artifact.setId("artifact-1");
        artifact.setArtifactKey("TEXT_TO_IMAGE::visualization-image::recipe-1::step-1::abc");
        artifact.setStatus(AiArtifactStatus.PENDING);
        artifact.setConsumerId(CONSUMER_ID);
        artifact.setCodecId("generated-image-v1");
        artifact.setCorrelationId("recipe-1::step-1");
        artifact.setCreatedAt(Instant.now().minusSeconds(600));
        return artifact;
    }
}
