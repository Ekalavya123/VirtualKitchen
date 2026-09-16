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
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.AIVisualizationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationImageArtifactConsumer;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The end-to-end proof of the AI artifact store.
 * <p>
 * Before this feature, a storage failure discarded the generated image bytes and the retry paid
 * the provider a second time. These tests assert the replacement behaviour directly: the payload
 * survives the failed upload, and the retry uploads that stored payload without generating again.
 */
class AIRecipeVisualizationServiceArtifactTest {

    private static final String RECIPE_ID = "recipe-1";
    private static final String STEP_ID = "step-1";
    private static final String VISUALIZATION_KEY = RECIPE_ID + "::" + STEP_ID;
    private static final byte[] IMAGE_BYTES = "generated-png-bytes".getBytes(StandardCharsets.UTF_8);

    private AIRecipeVisualizationService service;
    private ImageGenerationClient imageGenerationClient;
    private ImageStorageClient imageStorageClient;
    private AiArtifactService artifactService;
    private Map<String, VisualizationAsset> assetStore;

    /** Stands in for the persisted artifact row plus its payload, so reuse can be exercised in memory. */
    private final AtomicReference<AiArtifact> stagedArtifact = new AtomicReference<>();
    private final AtomicReference<GeneratedImage> stagedPayload = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        RecipeRepository recipeRepository = mock(RecipeRepository.class);
        AIVisualizationAssetRepository assetRepository = mock(AIVisualizationAssetRepository.class);
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
            assetStore.put(saved.getVisualizationKey(), saved);
            return saved;
        });

        when(aiClient.chat(any())).thenReturn(
                AIResponse.builder().content("{\"imagePrompt\":\"a knife\",\"videoPrompt\":\"v\"}").build());
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new GeneratedImage("image/png", IMAGE_BYTES));

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

        service = new AIRecipeVisualizationService(
                recipeRepository,
                assetRepository,
                sequenceGeneratorService,
                queueService,
                new AiClientResolver(Map.of("testTextClient", aiClient), Map.of("testImageClient", imageGenerationClient)),
                new AIVisualizationPromptBuilder(),
                artifactService,
                new GeneratedImageCodec(),
                consumer);
    }

    @Test
    void generateImage_uploadFails_retainsThePayloadAndRecordsTheRealCause() {
        when(imageStorageClient.upload(any(), anyString(), anyString()))
                .thenThrow(new AICommunicationException("Supabase storage upload failed with status: 503"));

        VisualizationAsset asset = service.resolveVisualizationAsset(
                1L, RECIPE_ID, STEP_ID, Map.of("action", "chop"), null);

        assertNull(asset.getImageUrl(), "a failed upload must not leave a URL behind");
        assertNotNull(asset.getImageFailureReason());
        assertTrue(asset.getImageFailureReason().contains("AICommunicationException"),
                "the real cause must reach the asset, not a generic message: " + asset.getImageFailureReason());

        assertNotNull(stagedArtifact.get(), "the paid payload must have been persisted before the upload");
        assertEquals(AiArtifactStatus.PENDING, stagedArtifact.get().getStatus(),
                "the artifact must stay pending so it can be recovered");
        verify(artifactService, never()).markConsumed(anyString());
    }

    /**
     * The whole point of the feature, in one assertion pair: two upload attempts, one generation.
     */
    @Test
    void resolveVisualizationAsset_afterAnUploadFailure_reusesThePayloadInsteadOfRegenerating() throws Exception {
        when(imageStorageClient.upload(any(), anyString(), anyString()))
                .thenThrow(new AICommunicationException("Supabase storage upload failed with status: 503"))
                .thenReturn("https://cdn.example/img.png");

        service.resolveVisualizationAsset(1L, RECIPE_ID, STEP_ID, Map.of("action", "chop"), null);
        VisualizationAsset recovered = service.resolveVisualizationAsset(
                1L, RECIPE_ID, STEP_ID, Map.of("action", "chop"), null);

        assertEquals("https://cdn.example/img.png", recovered.getImageUrl());
        assertNull(recovered.getImageFailureReason());
        verify(imageGenerationClient, times(1)).generate(anyString());
        verify(imageStorageClient, times(2)).upload(any(), anyString(), anyString());
        verify(artifactService, times(1)).markConsumed(anyString());
    }

    @Test
    void resolveVisualizationAsset_whenUploadSucceedsFirstTime_consumesTheArtifact() {
        when(imageStorageClient.upload(any(), anyString(), anyString()))
                .thenReturn("https://cdn.example/img.png");

        VisualizationAsset asset = service.resolveVisualizationAsset(
                1L, RECIPE_ID, STEP_ID, Map.of("action", "chop"), null);

        assertEquals("https://cdn.example/img.png", asset.getImageUrl());
        assertNull(asset.getImageFailureReason());
        verify(artifactService, times(1)).markConsumed(anyString());
        assertEquals(AiArtifactStatus.CONSUMED, stagedArtifact.get().getStatus());
    }

    /**
     * A mocked {@link AiArtifactService} with just enough real behaviour to exercise reuse:
     * staging records the payload as PENDING, lookup returns it while it is still PENDING, and
     * consuming flips it to CONSUMED so it stops being reusable.
     */
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
