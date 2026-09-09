package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationJobStatus;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.ai.repository.VisualizationJobRepository;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.AIVisualizationPromptBuilder;
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
        when(assetRepository.findByVisualizationKey(anyString())).thenReturn(Optional.empty());
        when(assetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiClient.chat(any())).thenReturn(
                AIResponse.builder().content("{\"imagePrompt\":\"p\",\"videoPrompt\":\"v\"}").build());
        when(imageStorageClient.upload(any(), anyString(), anyString())).thenReturn("https://cdn.example/img.png");

        AIRecipeVisualizationService aiRecipeVisualizationService = new AIRecipeVisualizationService(
                recipeRepository,
                assetRepository,
                sequenceGeneratorService,
                aiClient,
                imageGenerationClient,
                imageStorageClient,
                new AIVisualizationPromptBuilder());

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

        VisualizationJobResponseDTO started = service.startJob("flow-1");
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

        service.startJob("flow-2");

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

        assertThrows(RecipeFlowGenerationException.class, () -> service.startJob("missing"));

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
