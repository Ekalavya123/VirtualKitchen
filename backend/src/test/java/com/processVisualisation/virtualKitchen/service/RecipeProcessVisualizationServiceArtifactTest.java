package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactHit;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactStatus;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.GeneratedImageCodec;
import com.processVisualisation.virtualKitchen.ai.credit.AiCreditProperties;
import com.processVisualisation.virtualKitchen.ai.credit.AiCreditTransactionRepository;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.queue.AiQueueProperties;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestJobRepository;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestProperties;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessVisualizationInput;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessVisualizationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationImageArtifactConsumer;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the failure-safe generate-then-store lifecycle for the new Process-model visualization
 * service.
 */
class RecipeProcessVisualizationServiceArtifactTest {

    private static final Long RECIPE_ID = 42L;
    private static final String STEP_ID = "step-1";
    private static final String VISUALIZATION_KEY = "42::" + STEP_ID;
    private static final byte[] IMAGE_BYTES = "generated-png-bytes".getBytes(StandardCharsets.UTF_8);

    private RecipeProcessVisualizationService service;
    private ImageGenerationClient imageGenerationClient;
    private ImageStorageClient imageStorageClient;
    private AIVisualizationAssetRepository assetRepository;
    private AiArtifactService artifactService;
    private Map<String, VisualizationAsset> assetStore;

    private final AtomicReference<AiArtifact> stagedArtifact = new AtomicReference<>();
    private final AtomicReference<GeneratedImage> stagedPayload = new AtomicReference<>();
    /** When true, the next {@code assetRepository.save} carrying a resolved imageUrl throws once, then resets. */
    private final java.util.concurrent.atomic.AtomicBoolean failNextImageUrlSave = new java.util.concurrent.atomic.AtomicBoolean(false);

    @BeforeEach
    void setUp() throws Exception {
        ProcessRepository processRepository = mock(ProcessRepository.class);
        assetRepository = mock(AIVisualizationAssetRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        AIClient aiClient = mock(AIClient.class);
        imageGenerationClient = mock(ImageGenerationClient.class);
        imageStorageClient = mock(ImageStorageClient.class);

        assetStore = new ConcurrentHashMap<>();
        AtomicLong idCounter = new AtomicLong();
        when(sequenceGeneratorService.generateSequence(anyString())).thenAnswer(inv -> idCounter.incrementAndGet());
        when(assetRepository.findByVisualizationKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.<String>getArgument(0))));
        when(assetRepository.save(any())).thenAnswer(inv -> {
            VisualizationAsset saved = inv.getArgument(0);
            if (saved.getImageUrl() != null && failNextImageUrlSave.compareAndSet(true, false)) {
                throw new RuntimeException("Mongo write failed");
            }
            assetStore.put(saved.getVisualizationKey(), saved);
            return saved;
        });

        when(aiClient.chat(any())).thenReturn(AIResponse.builder().content("{\"imagePrompt\":\"a knife\"}").build());
        when(imageGenerationClient.generate(anyString())).thenReturn(new GeneratedImage("image/png", IMAGE_BYTES));

        ModelDefinition textModel = model("test-text", AiCapability.TEXT_TO_TEXT, "testTextClient");
        ModelDefinition imageModel = model("test-image", AiCapability.TEXT_TO_IMAGE, "testImageClient");
        AiModelProperties modelProperties = new AiModelProperties();
        modelProperties.setModels(List.of(textModel, imageModel));
        modelProperties.setDefaultModel(Map.of(
                AiCapability.TEXT_TO_TEXT.name(), "test-text",
                AiCapability.TEXT_TO_IMAGE.name(), "test-image"));
        AiModelRegistry modelRegistry = new AiModelRegistry(modelProperties);

        CreditService creditService = new CreditService(
                mock(MongoTemplate.class), new AiCreditProperties(), mock(AiCreditTransactionRepository.class));

        AiRequestJobRepository jobRepository = mock(AiRequestJobRepository.class);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        artifactService = inMemoryArtifactService();

        AiRequestQueueService queueService = new AiRequestQueueService(
                jobRepository,
                new ModelSelectionService(modelRegistry, creditService),
                creditService,
                new ThreadPoolTaskPool(2, "test-ai-text"),
                new AiQueueProperties(),
                new AiRequestProperties(),
                artifactService,
                modelRegistry);

        VisualizationImageArtifactConsumer consumer = new VisualizationImageArtifactConsumer(
                imageStorageClient, assetRepository, mock(MongoTemplate.class));

        service = new RecipeProcessVisualizationService(
                processRepository,
                assetRepository,
                sequenceGeneratorService,
                queueService,
                new AiClientResolver(Map.of("testTextClient", aiClient), Map.of("testImageClient", imageGenerationClient)),
                new RecipeProcessVisualizationPromptBuilder(),
                artifactService,
                new GeneratedImageCodec(),
                consumer,
                new RecipeStepVocabularyProvider());
    }

    private RecipeProcessVisualizationInput input() {
        return new RecipeProcessVisualizationInput(
                "Cut",
                List.of(new RecipeProcessVisualizationInput.IngredientTarget("Onion", "2", "", "Medium")),
                List.of(),
                "Cut the onions",
                "Chopped onions",
                "", "", "",
                null);
    }

    @Test
    void generateImage_uploadFails_retainsThePayloadAndRecordsTheRealCause() throws Exception {
        when(imageStorageClient.upload(any(), anyString(), anyString()))
                .thenThrow(new AICommunicationException("Supabase storage upload failed with status: 503"));

        VisualizationAsset asset = service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());

        assertNull(asset.getImageUrl(), "a failed upload must not leave a URL behind");
        assertNotNull(asset.getImageFailureReason());
        assertTrue(asset.getImageFailureReason().contains("AICommunicationException"));

        assertNotNull(stagedArtifact.get(), "the paid payload must have been persisted before the upload");
        assertEquals(AiArtifactStatus.PENDING, stagedArtifact.get().getStatus());
        verify(artifactService, never()).markConsumed(anyString());
        verify(imageGenerationClient, times(1)).generate(anyString());
    }

    @Test
    void resolveVisualizationAsset_afterAnUploadFailure_reusesThePayloadInsteadOfRegenerating() throws Exception {
        when(imageStorageClient.upload(any(), anyString(), anyString()))
                .thenThrow(new AICommunicationException("Supabase storage upload failed with status: 503"))
                .thenReturn("https://cdn.example/img.png");

        service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());
        VisualizationAsset recovered = service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());

        assertEquals("https://cdn.example/img.png", recovered.getImageUrl());
        assertNull(recovered.getImageFailureReason());
        verify(imageGenerationClient, times(1)).generate(anyString());
        verify(imageStorageClient, times(2)).upload(any(), anyString(), anyString());
        verify(artifactService, times(1)).markConsumed(anyString());
    }

    @Test
    void resolveVisualizationAsset_whenUploadSucceedsFirstTime_consumesTheArtifact() throws Exception {
        when(imageStorageClient.upload(any(), anyString(), anyString())).thenReturn("https://cdn.example/img.png");

        VisualizationAsset asset = service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());

        assertEquals("https://cdn.example/img.png", asset.getImageUrl());
        verify(artifactService, times(1)).markConsumed(anyString());
        assertEquals(AiArtifactStatus.CONSUMED, stagedArtifact.get().getStatus());
    }

    @Test
    void resolveVisualizationAsset_existingAssetWithImageUrl_makesNoAiOrImageCall() throws Exception {
        VisualizationAsset existing = new VisualizationAsset();
        existing.setId(99L);
        existing.setVisualizationKey(VISUALIZATION_KEY);
        existing.setImagePrompt("already prompted");
        existing.setImageUrl("https://cdn.example/already-uploaded.png");
        assetStore.put(VISUALIZATION_KEY, existing);

        VisualizationAsset result = service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());

        assertEquals("https://cdn.example/already-uploaded.png", result.getImageUrl());
        verify(imageGenerationClient, never()).generate(anyString());
        verify(imageStorageClient, never()).upload(any(), anyString(), anyString());
    }

    /**
     * The final `assetRepository.save(asset)` inside `generateImage` sits *outside* the durable
     * sequence: by the time it runs, the consumer has already written `imageUrl` straight to the
     * asset's DB row (see `VisualizationImageArtifactConsumer.consume`, a targeted `updateFirst`
     * keyed by `visualizationKey`) and already retired the artifact. So even if this final save
     * throws, a retry finds the asset already carrying its `imageUrl` and makes no further AI or
     * upload call — this test simulates that write failing and proves exactly that.
     */
    @Test
    void resolveVisualizationAsset_finalAssetSaveFails_retryReusesTheAlreadyPersistedImageUrl() throws Exception {
        when(imageStorageClient.upload(any(), anyString(), anyString())).thenReturn("https://cdn.example/img.png");

        // Fails only the save that carries a resolved imageUrl (generateImage's final, unguarded
        // save) — the earlier prompt-only save (from generatePrompt) must still succeed normally,
        // isolating "the final DB write of imageUrl fails" from everything before it. `asset` is
        // the same object reference throughout generateImage, so by the time this final save runs,
        // the in-place mutation already carries the imageUrl — mirroring how
        // VisualizationImageArtifactConsumer.consume's own targeted `updateFirst` would already have
        // written it durably to the real DB row before this outer save ever runs.
        failNextImageUrlSave.set(true);

        assertThrows(RuntimeException.class, () -> service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input()));

        VisualizationAsset retried = service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, input());

        assertEquals("https://cdn.example/img.png", retried.getImageUrl());
        verify(imageGenerationClient, times(1)).generate(anyString());
        verify(imageStorageClient, times(1)).upload(any(), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private AiArtifactService inMemoryArtifactService() throws Exception {
        AiArtifactService stub = mock(AiArtifactService.class);

        when(stub.stage(any(), any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    AiArtifactSpec<GeneratedImage> spec = inv.getArgument(4);
                    AiArtifact artifact = new AiArtifact();
                    artifact.setId("artifact-1");
                    artifact.setUserId(inv.getArgument(0));
                    artifact.setArtifactKey(spec.artifactKey());
                    artifact.setCapability(inv.getArgument(1));
                    artifact.setCorrelationType(inv.getArgument(2));
                    artifact.setCorrelationId(inv.getArgument(3));
                    artifact.setStatus(AiArtifactStatus.PENDING);
                    artifact.setProducedByModelKey("test-image");
                    artifact.setProducedByTier(ModelTier.OPEN_SOURCE);
                    artifact.setPayloadSize(IMAGE_BYTES.length);
                    stagedArtifact.set(artifact);
                    stagedPayload.set((GeneratedImage) inv.getArgument(5));
                    return artifact;
                });

        when(stub.findReusable(any(), any())).thenAnswer(inv -> {
            AiArtifact artifact = stagedArtifact.get();
            AiArtifactSpec<GeneratedImage> spec = inv.getArgument(1);
            if (artifact == null
                    || artifact.getStatus() != AiArtifactStatus.PENDING
                    || !artifact.getArtifactKey().equals(spec.artifactKey())) {
                return Optional.empty();
            }
            return Optional.of(new AiArtifactHit<>(artifact, stagedPayload.get()));
        });

        when(stub.markConsumed(anyString())).thenAnswer(inv -> {
            AiArtifact artifact = stagedArtifact.get();
            if (artifact == null || artifact.getStatus() != AiArtifactStatus.PENDING) {
                return false;
            }
            artifact.setStatus(AiArtifactStatus.CONSUMED);
            return true;
        });

        return stub;
    }

    private ModelDefinition model(String key, AiCapability capability, String providerBean) {
        ModelDefinition definition = new ModelDefinition();
        definition.setKey(key);
        definition.setCapability(capability);
        definition.setTier(ModelTier.OPEN_SOURCE);
        definition.setProviderBean(providerBean);
        definition.setProviderModelId(key + "-model");
        definition.setEnabled(true);
        return definition;
    }
}
