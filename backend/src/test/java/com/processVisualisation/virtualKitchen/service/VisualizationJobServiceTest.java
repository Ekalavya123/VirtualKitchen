package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.GeneratedImageCodec;
import com.processVisualisation.virtualKitchen.ai.credit.AiCreditProperties;
import com.processVisualisation.virtualKitchen.ai.credit.AiCreditTransactionRepository;
import com.processVisualisation.virtualKitchen.ai.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJobStatus;
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
import com.processVisualisation.virtualKitchen.ai.repository.VisualizationJobRepository;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.AIVisualizationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationImageArtifactConsumer;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationJobService;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisualizationJobServiceTest {

    private RecipeRepository recipeRepository;
    private AIVisualizationAssetRepository assetRepository;
    private ImageGenerationClient imageGenerationClient;
    private VisualizationJobRepository visualizationJobRepository;
    private MongoTemplate mongoTemplate;
    private VisualizationJobService service;

    @BeforeEach
    void setUp() {
        recipeRepository = mock(RecipeRepository.class);
        assetRepository = mock(AIVisualizationAssetRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        AIClient aiClient = mock(AIClient.class);
        imageGenerationClient = mock(ImageGenerationClient.class);
        ImageStorageClient imageStorageClient = mock(ImageStorageClient.class);

        AtomicLong idCounter = new AtomicLong(0);
        when(sequenceGeneratorService.generateSequence(anyString())).thenAnswer(inv -> idCounter.incrementAndGet());
        // Store-backed rather than always-empty: the image consumer looks the asset back up by
        // visualizationKey to attach the uploaded URL, which only works once generatePrompt has
        // saved it. Each step still has its own key, so no step ever sees another's asset.
        Map<String, VisualizationAsset> assetStore = new ConcurrentHashMap<>();
        when(assetRepository.findByVisualizationKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.<String>getArgument(0))));
        when(assetRepository.save(any())).thenAnswer(inv -> {
            VisualizationAsset saved = inv.getArgument(0);
            assetStore.put(saved.getVisualizationKey(), saved);
            return saved;
        });
        when(aiClient.chat(any())).thenReturn(
                AIResponse.builder().content("{\"imagePrompt\":\"p\",\"videoPrompt\":\"v\"}").build());
        when(imageStorageClient.upload(any(), anyString(), anyString())).thenReturn("https://cdn.example/img.png");

        // Wire the real model-selection/queue stack with two OPEN_SOURCE test models (zero credit
        // cost) pointing at the mocked AIClient/ImageGenerationClient above, so
        // AIRecipeVisualizationService's generatePrompt/generateImage exercise the same
        // AiRequestQueueService.executeInline path production code goes through, without needing
        // Mongo-backed credit reservations for this test (OPEN_SOURCE models never reserve).
        ModelDefinition textModel = new ModelDefinition();
        textModel.setKey("test-text");
        textModel.setCapability(AiCapability.TEXT_TO_TEXT);
        textModel.setTier(ModelTier.OPEN_SOURCE);
        textModel.setProviderBean("testTextClient");
        textModel.setProviderModelId("test-text-model");
        textModel.setEnabled(true);

        ModelDefinition imageModel = new ModelDefinition();
        imageModel.setKey("test-image");
        imageModel.setCapability(AiCapability.TEXT_TO_IMAGE);
        imageModel.setTier(ModelTier.OPEN_SOURCE);
        imageModel.setProviderBean("testImageClient");
        imageModel.setProviderModelId("test-image-model");
        imageModel.setEnabled(true);

        AiModelProperties modelProperties = new AiModelProperties();
        modelProperties.setModels(List.of(textModel, imageModel));
        modelProperties.setDefaultModel(Map.of(
                AiCapability.TEXT_TO_TEXT.name(), "test-text",
                AiCapability.TEXT_TO_IMAGE.name(), "test-image"));
        AiModelRegistry modelRegistry = new AiModelRegistry(modelProperties);

        CreditService creditService = new CreditService(
                mock(MongoTemplate.class), new AiCreditProperties(), mock(AiCreditTransactionRepository.class));
        ModelSelectionService modelSelectionService = new ModelSelectionService(modelRegistry, creditService);

        AiClientResolver clientResolver = new AiClientResolver(
                Map.of("testTextClient", aiClient), Map.of("testImageClient", imageGenerationClient));

        AiRequestJobRepository aiRequestJobRepository = mock(AiRequestJobRepository.class);
        when(aiRequestJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Artifact staging is stubbed out here: this test is about the job pipeline, not the
        // artifact store. findReusable must be stubbed explicitly because the queue maps over the
        // returned Optional, and stage() returning null exercises the degraded path where the
        // upload still happens against a detached artifact.
        AiArtifactService artifactService = mock(AiArtifactService.class);
        when(artifactService.findReusable(any(), any())).thenReturn(Optional.empty());

        AiRequestQueueService queueService = new AiRequestQueueService(
                aiRequestJobRepository,
                modelSelectionService,
                creditService,
                new ThreadPoolTaskPool(2, "test-ai-text"),
                new AiQueueProperties(),
                new AiRequestProperties(),
                artifactService,
                modelRegistry);

        VisualizationImageArtifactConsumer imageArtifactConsumer = new VisualizationImageArtifactConsumer(
                imageStorageClient, assetRepository, mock(MongoTemplate.class));

        AIRecipeVisualizationService aiRecipeVisualizationService = new AIRecipeVisualizationService(
                recipeRepository,
                assetRepository,
                sequenceGeneratorService,
                queueService,
                clientResolver,
                new AIVisualizationPromptBuilder(),
                artifactService,
                new GeneratedImageCodec(),
                imageArtifactConsumer);

        visualizationJobRepository = mock(VisualizationJobRepository.class);
        when(visualizationJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mongoTemplate = mock(MongoTemplate.class);

        service = new VisualizationJobService(
                aiRecipeVisualizationService,
                visualizationJobRepository,
                mongoTemplate,
                new ThreadPoolTaskPool(2, "test-visualization"),
                new ThreadPoolTaskPool(1, "test-orchestrator"));
    }

    @Test
    void startJob_happyPath_savesFlowExactlyOnceAndRecordsEveryStep() throws Exception {
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1, 2, 3}));
        Recipe flow = flowWithSteps(3);
        when(recipeRepository.findByFlowId("flow-1")).thenReturn(Optional.of(flow));

        VisualizationJobResponseDTO started = service.startJob(1L, "flow-1");
        assertEquals(3, started.getTotalSteps());
        assertEquals("QUEUED", started.getStatus());

        // Only the reduce step saves the shared flow document, and it must happen exactly once
        // regardless of how many steps ran concurrently — this is the lost-update-fix assertion.
        verify(recipeRepository, timeout(5000).times(1)).save(flow);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(4))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(com.processVisualisation.virtualKitchen.ai.model.VisualizationJob.class));

        List<Update> updates = updateCaptor.getAllValues();
        long stepIncrementCount = updates.stream().filter(u -> incValue(u, "completedSteps") == 1).count();
        assertEquals(3, stepIncrementCount, "expected exactly one completedSteps increment per step");

        boolean sawCompleted = updates.stream()
                .anyMatch(u -> statusValue(u) == VisualizationJobStatus.COMPLETED);
        assertTrue(sawCompleted);
    }

    @Test
    void startJob_partialFailure_stillSavesOnceAndMarksCompletedWithErrors() throws Exception {
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1}))
                .thenThrow(new RuntimeException("image generation failed"))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1}));

        Recipe flow = flowWithSteps(3);
        when(recipeRepository.findByFlowId("flow-2")).thenReturn(Optional.of(flow));

        service.startJob(1L, "flow-2");

        verify(recipeRepository, timeout(5000).times(1)).save(flow);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(4))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(com.processVisualisation.virtualKitchen.ai.model.VisualizationJob.class));

        List<Update> updates = updateCaptor.getAllValues();
        long stepIncrementCount = updates.stream().filter(u -> incValue(u, "completedSteps") == 1).count();
        assertEquals(3, stepIncrementCount, "a failed step still counts toward completedSteps");

        boolean sawCompletedWithErrors = updates.stream()
                .anyMatch(u -> statusValue(u) == VisualizationJobStatus.COMPLETED_WITH_ERRORS);
        assertTrue(sawCompletedWithErrors);
    }

    @Test
    void startJob_recipeNotFound_throwsSynchronouslyWithoutCreatingAJob() {
        when(recipeRepository.findByFlowId("missing")).thenReturn(Optional.empty());

        assertThrows(RecipeFlowGenerationException.class, () -> service.startJob(1L, "missing"));

        verify(visualizationJobRepository, never()).save(any());
    }

    private Recipe flowWithSteps(int count) {
        Recipe flow = new Recipe();
        flow.setFlowId("flow-under-test");
        List<Recipe.NodeDocument> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Recipe.NodeDocument node = new Recipe.NodeDocument();
            node.setId("node-" + i);
            node.setType("recipeStepNode");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("title", "Step " + i);
            data.put("action", "action-" + i);
            node.setData(data);
            nodes.add(node);
        }
        flow.setNodes(nodes);
        flow.setEdges(new ArrayList<>());
        return flow;
    }

    private int incValue(Update update, String field) {
        Document updateObject = update.getUpdateObject();
        Document inc = (Document) updateObject.get("$inc");
        if (inc == null || !inc.containsKey(field)) {
            return 0;
        }
        return ((Number) inc.get(field)).intValue();
    }

    private VisualizationJobStatus statusValue(Update update) {
        Document updateObject = update.getUpdateObject();
        Document set = (Document) updateObject.get("$set");
        if (set == null || !set.containsKey("status")) {
            return null;
        }
        return (VisualizationJobStatus) set.get("status");
    }
}
