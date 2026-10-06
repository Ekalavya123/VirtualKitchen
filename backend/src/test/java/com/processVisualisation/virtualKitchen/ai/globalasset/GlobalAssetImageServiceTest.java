package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.GeneratedImageCodec;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetBatchResponseDTO;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetImageResponseDTO;
import com.processVisualisation.virtualKitchen.ai.globalasset.dto.GlobalAssetImageResponseDTO.Outcome;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.queue.AiWork;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.common.concurrent.NamedTask;
import com.processVisualisation.virtualKitchen.common.concurrent.TaskPool;
import com.processVisualisation.virtualKitchen.common.exception.GlobalAssetException;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalAssetImageServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");
    private static final long LEASE_MS = 900_000L;
    private static final GlobalResource ONION =
            new GlobalResource(GlobalResourceType.INGREDIENT, 42L, "Onion", "Red onion", null);
    private static final String ONION_KEY = "global::INGREDIENT::42";

    private GlobalResourceResolver resolver;
    private GlobalAssetStore store;
    private AiRequestQueueService queueService;
    private AiClientResolver clientResolver;
    private AiArtifactService artifactService;
    private GlobalAssetImageArtifactConsumer consumer;
    private TaskPool pool;
    private final List<NamedTask<?>> submitted = new ArrayList<>();
    private GlobalAssetImageService service;

    @BeforeEach
    void setUp() {
        resolver = mock(GlobalResourceResolver.class);
        store = mock(GlobalAssetStore.class);
        queueService = mock(AiRequestQueueService.class);
        clientResolver = mock(AiClientResolver.class);
        artifactService = mock(AiArtifactService.class);
        consumer = mock(GlobalAssetImageArtifactConsumer.class);
        pool = mock(TaskPool.class);
        when(pool.submit(any())).thenAnswer(invocation -> {
            submitted.add(invocation.getArgument(0));
            return null;
        });
        service = new GlobalAssetImageService(resolver, store, new GlobalAssetPromptBuilder(), queueService,
                clientResolver, artifactService, mock(GeneratedImageCodec.class), consumer, pool, LEASE_MS,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(resolver.find(GlobalResourceType.INGREDIENT, 42L)).thenReturn(Optional.of(ONION));
    }

    // --- request: idempotency and duplicate protection ---

    @Test
    void request_queuesGenerationWhenResourceHasNoImage() {
        when(store.find(ONION_KEY)).thenReturn(Optional.empty());
        when(store.claim(eq(ONION), anyString(), eq(ADMIN_ID), eq(NOW), eq(NOW.plusMillis(LEASE_MS))))
                .thenReturn(Optional.of(asset(7L, GlobalAssetStatus.QUEUED, null)));

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.QUEUED);
        assertThat(result.getStatus()).isEqualTo(GlobalAssetStatus.QUEUED);
        assertThat(result.getAssetId()).isEqualTo(7L);
        assertThat(result.getResourceName()).isEqualTo("Onion");
        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).taskId()).isEqualTo("global-asset:" + ONION_KEY);
    }

    @Test
    void request_returnsAlreadyExistsWithoutGeneratingWhenCatalogHasImage() {
        GlobalResource withImage = new GlobalResource(GlobalResourceType.INGREDIENT, 42L, "Onion", null, "https://img/onion.png");
        when(resolver.find(GlobalResourceType.INGREDIENT, 42L)).thenReturn(Optional.of(withImage));
        when(store.find(ONION_KEY)).thenReturn(Optional.empty());

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(result.getImageUrl()).isEqualTo("https://img/onion.png");
        verify(store, never()).claim(any(), any(), any(), any(), any());
        assertThat(submitted).isEmpty();
    }

    @Test
    void request_republishesReadyAssetInsteadOfRegenerating() {
        when(store.find(ONION_KEY)).thenReturn(Optional.of(asset(7L, GlobalAssetStatus.READY, "https://img/a.png")));

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.ALREADY_EXISTS);
        assertThat(result.getImageUrl()).isEqualTo("https://img/a.png");
        verify(resolver).publishImage(GlobalResourceType.INGREDIENT, 42L, "https://img/a.png");
        verify(store, never()).claim(any(), any(), any(), any(), any());
        assertThat(submitted).isEmpty();
    }

    @Test
    void request_returnsInProgressWithoutNewJobWhenGenerationRunning() {
        VisualizationAsset running = asset(7L, GlobalAssetStatus.GENERATING, null);
        when(store.find(ONION_KEY)).thenReturn(Optional.of(running));
        when(store.claim(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.IN_PROGRESS);
        assertThat(result.getStatus()).isEqualTo(GlobalAssetStatus.GENERATING);
        assertThat(result.getAssetId()).isEqualTo(7L);
        assertThat(submitted).isEmpty();
    }

    @Test
    void request_lostInsertRaceJoinsTheWinnerInsteadOfQueueingAgain() {
        // No asset when first read; a concurrent request inserts it before our claim.
        when(store.find(ONION_KEY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(asset(8L, GlobalAssetStatus.QUEUED, null)));
        when(store.claim(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.IN_PROGRESS);
        assertThat(result.getAssetId()).isEqualTo(8L);
        assertThat(submitted).isEmpty();
    }

    @Test
    void request_requeuesAPreviouslyFailedGeneration() {
        VisualizationAsset failed = asset(7L, GlobalAssetStatus.FAILED, null);
        failed.setImageFailureReason("AICommunicationException: down");
        when(store.find(ONION_KEY)).thenReturn(Optional.of(failed));
        when(store.claim(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(asset(7L, GlobalAssetStatus.QUEUED, null)));

        GlobalAssetImageResponseDTO result = service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID);

        assertThat(result.getOutcome()).isEqualTo(Outcome.QUEUED);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void request_unknownResourceIsNotFound() {
        when(resolver.find(GlobalResourceType.INGREDIENT, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(GlobalResourceType.INGREDIENT, 99L, ADMIN_ID))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(submitted).isEmpty();
    }

    @Test
    void request_resourceWithoutNameIsNotEligible() {
        when(resolver.find(GlobalResourceType.EQUIPMENT, 5L))
                .thenReturn(Optional.of(new GlobalResource(GlobalResourceType.EQUIPMENT, 5L, " ", null, null)));

        assertThatThrownBy(() -> service.request(GlobalResourceType.EQUIPMENT, 5L, ADMIN_ID))
                .isInstanceOf(GlobalAssetException.class)
                .satisfies(e -> assertThat(((GlobalAssetException) e).getStatus().value()).isEqualTo(422));
    }

    @Test
    void request_releasesClaimWhenTheTaskCannotBeQueued() {
        when(store.find(ONION_KEY)).thenReturn(Optional.empty());
        when(store.claim(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(asset(7L, GlobalAssetStatus.QUEUED, null)));
        when(pool.submit(any())).thenThrow(new IllegalStateException("pool shut down"));

        assertThatThrownBy(() -> service.request(GlobalResourceType.INGREDIENT, 42L, ADMIN_ID))
                .isInstanceOf(IllegalStateException.class);
        verify(store).markFailed(eq(ONION_KEY), anyString(), anyString());
    }

    // --- generate: the queued work ---

    @Test
    @SuppressWarnings("unchecked")
    void generate_storesAndPublishesTheImageThroughTheArtifactPipeline() throws Exception {
        when(store.markGenerating(eq(ONION_KEY), eq("tok"), anyString())).thenReturn(true);
        GeneratedImage image = new GeneratedImage("image/png", new byte[]{1, 2, 3});
        AiArtifact artifact = new AiArtifact();
        artifact.setId("art-1");
        artifact.setCorrelationId(ONION_KEY);
        when(queueService.executeInline(eq(ADMIN_ID), eq(AiCapability.TEXT_TO_IMAGE), isNull(), isNull(),
                eq("global-asset-image"), eq(ONION_KEY), any(AiArtifactSpec.class), any(AiWork.class)))
                .thenReturn(AiRequestOutcome.fresh("job-1", image, selection(), artifact));

        service.generate(ONION, ONION_KEY, "tok", ADMIN_ID);

        ArgumentCaptor<AiArtifactSpec<GeneratedImage>> spec = ArgumentCaptor.forClass(AiArtifactSpec.class);
        verify(queueService).executeInline(eq(ADMIN_ID), eq(AiCapability.TEXT_TO_IMAGE), isNull(), isNull(),
                eq("global-asset-image"), eq(ONION_KEY), spec.capture(), any(AiWork.class));
        assertThat(spec.getValue().consumerId()).isEqualTo(GlobalAssetImageArtifactConsumer.CONSUMER_ID);
        verify(consumer).consume(artifact, image);
        verify(artifactService).markConsumed("art-1");
        verify(store, never()).markFailed(any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_callsTheSelectedImageProviderWithTheIngredientPrompt() throws Exception {
        when(store.markGenerating(eq(ONION_KEY), eq("tok"), anyString())).thenReturn(true);
        ImageGenerationClient imageClient = mock(ImageGenerationClient.class);
        when(clientResolver.resolveImageClient(any())).thenReturn(imageClient);
        when(queueService.executeInline(any(), any(), any(), any(), any(), any(), any(AiArtifactSpec.class), any(AiWork.class)))
                .thenAnswer(invocation -> {
                    AiWork<GeneratedImage> work = invocation.getArgument(7);
                    return AiRequestOutcome.fresh("job-1", work.run(selection()), selection(), null);
                });
        when(imageClient.generate(anyString())).thenReturn(new GeneratedImage("image/png", new byte[]{1}));

        service.generate(ONION, ONION_KEY, "tok", ADMIN_ID);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(imageClient).generate(prompt.capture());
        assertThat(prompt.getValue()).contains("Onion").contains("Red onion");
        verify(store).markGenerating(ONION_KEY, "tok", prompt.getValue());
        // Without a stored artifact the consumer still runs over a transient one carrying the asset key.
        ArgumentCaptor<AiArtifact> artifact = ArgumentCaptor.forClass(AiArtifact.class);
        verify(consumer).consume(artifact.capture(), any());
        assertThat(artifact.getValue().getCorrelationId()).isEqualTo(ONION_KEY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_recordsProviderFailureOnTheAsset() {
        when(store.markGenerating(eq(ONION_KEY), eq("tok"), anyString())).thenReturn(true);
        when(queueService.executeInline(any(), any(), any(), any(), any(), any(), any(AiArtifactSpec.class), any(AiWork.class)))
                .thenThrow(new AICommunicationException("provider unavailable"));

        service.generate(ONION, ONION_KEY, "tok", ADMIN_ID);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(store).markFailed(eq(ONION_KEY), eq("tok"), reason.capture());
        assertThat(reason.getValue()).contains("AICommunicationException").contains("provider unavailable");
        verify(artifactService, never()).markConsumed(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_recordsStorageFailureAndLeavesArtifactForRecovery() throws Exception {
        when(store.markGenerating(eq(ONION_KEY), eq("tok"), anyString())).thenReturn(true);
        AiArtifact artifact = new AiArtifact();
        artifact.setId("art-1");
        when(queueService.executeInline(any(), any(), any(), any(), any(), any(), any(AiArtifactSpec.class), any(AiWork.class)))
                .thenReturn(AiRequestOutcome.fresh("job-1", new GeneratedImage("image/png", new byte[]{1}), selection(), artifact));
        when(consumer.consume(any(), any())).thenThrow(new IllegalStateException("upload failed"));

        service.generate(ONION, ONION_KEY, "tok", ADMIN_ID);

        verify(store).markFailed(eq(ONION_KEY), eq("tok"), anyString());
        verify(artifactService, never()).markConsumed(anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void generate_skipsWorkWhenItsClaimWasSuperseded() {
        when(store.markGenerating(any(), any(), any())).thenReturn(false);

        service.generate(ONION, ONION_KEY, "tok", ADMIN_ID);

        verify(queueService, never()).executeInline(any(), any(), any(), any(), any(), any(), any(AiArtifactSpec.class), any(AiWork.class));
    }

    // --- batch ---

    @Test
    void requestBatch_reportsEachOutcome() {
        when(store.find(ONION_KEY)).thenReturn(Optional.empty());
        when(store.claim(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(asset(7L, GlobalAssetStatus.QUEUED, null)));
        when(resolver.find(GlobalResourceType.INGREDIENT, 43L)).thenReturn(Optional.of(
                new GlobalResource(GlobalResourceType.INGREDIENT, 43L, "Salt", null, "https://img/salt.png")));
        when(resolver.find(GlobalResourceType.INGREDIENT, 99L)).thenReturn(Optional.empty());

        GlobalAssetBatchResponseDTO result =
                service.requestBatch(GlobalResourceType.INGREDIENT, List.of(42L, 43L, 99L, 42L), ADMIN_ID);

        assertThat(result.getRequested()).isEqualTo(3);
        assertThat(result.getQueued()).isEqualTo(1);
        assertThat(result.getAlreadyExists()).isEqualTo(1);
        assertThat(result.getNotFound()).isEqualTo(1);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void requestBatch_rejectsEmptyAndOversizedLists() {
        assertThatThrownBy(() -> service.requestBatch(GlobalResourceType.INGREDIENT, List.of(), ADMIN_ID))
                .isInstanceOf(GlobalAssetException.class);
        assertThatThrownBy(() -> service.requestBatch(GlobalResourceType.INGREDIENT, null, ADMIN_ID))
                .isInstanceOf(GlobalAssetException.class);
        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= GlobalAssetImageService.MAX_BATCH_IDS + 1; id++) {
            tooMany.add(id);
        }
        assertThatThrownBy(() -> service.requestBatch(GlobalResourceType.INGREDIENT, tooMany, ADMIN_ID))
                .isInstanceOf(GlobalAssetException.class);
        assertThatThrownBy(() -> service.requestBatch(GlobalResourceType.INGREDIENT,
                Collections.singletonList(null), ADMIN_ID))
                .isInstanceOf(GlobalAssetException.class);
    }

    @Test
    void requestMissing_queuesOnlyResourcesWithoutImages() {
        GlobalResource unnamed = new GlobalResource(GlobalResourceType.INGREDIENT, 50L, "", null, null);
        when(resolver.findMissingImages(GlobalResourceType.INGREDIENT)).thenReturn(List.of(ONION, unnamed));
        when(store.find(ONION_KEY)).thenReturn(Optional.empty());
        when(store.claim(eq(ONION), any(), any(), any(), any()))
                .thenReturn(Optional.of(asset(7L, GlobalAssetStatus.QUEUED, null)));

        GlobalAssetBatchResponseDTO result = service.requestMissing(GlobalResourceType.INGREDIENT, ADMIN_ID);

        assertThat(result.getRequested()).isEqualTo(2);
        assertThat(result.getQueued()).isEqualTo(1);
        assertThat(result.getNotEligible()).isEqualTo(1);
        assertThat(submitted).hasSize(1);
    }

    @Test
    void status_reportsFailureReason() {
        VisualizationAsset failed = asset(7L, GlobalAssetStatus.FAILED, null);
        failed.setImageFailureReason("NoAvailableModelException: none");
        when(store.find(ONION_KEY)).thenReturn(Optional.of(failed));

        GlobalAssetImageResponseDTO result = service.status(GlobalResourceType.INGREDIENT, 42L);

        assertThat(result.getStatus()).isEqualTo(GlobalAssetStatus.FAILED);
        assertThat(result.getFailureReason()).contains("NoAvailableModelException");
        assertThat(result.getOutcome()).isNull();
    }

    private static VisualizationAsset asset(Long id, GlobalAssetStatus status, String imageUrl) {
        VisualizationAsset asset = new VisualizationAsset();
        asset.setId(id);
        asset.setVisualizationKey(ONION_KEY);
        asset.setResourceType(GlobalResourceType.INGREDIENT);
        asset.setResourceId(42L);
        asset.setGenerationStatus(status);
        asset.setImageUrl(imageUrl);
        return asset;
    }

    private static ModelSelectionOutcome selection() {
        ModelDefinition model = new ModelDefinition();
        model.setKey("gemini-image");
        model.setTier(ModelTier.PAID);
        return new ModelSelectionOutcome(model, false, null, null);
    }
}
