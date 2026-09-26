package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
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
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationInput;
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.StepVocabularyProvider;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationImageArtifactConsumer;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the Process-model-specific behaviour {@code ProcessVisualizationServiceArtifactTest}
 * doesn't: STEP-only filtering (CONDITION skipped), edge-based ordering, multi-ingredient Action On
 * extraction, subprocess name resolution, and previous-step continuity chaining.
 */
class ProcessVisualizationServiceTest {

    private ProcessRepository processRepository;
    private ProcessVisualizationService service;

    @BeforeEach
    void setUp() {
        processRepository = mock(ProcessRepository.class);
        AIVisualizationAssetRepository assetRepository = mock(AIVisualizationAssetRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        AIClient aiClient = mock(AIClient.class);
        ImageGenerationClient imageGenerationClient = mock(ImageGenerationClient.class);
        ImageStorageClient imageStorageClient = mock(ImageStorageClient.class);

        Map<String, VisualizationAsset> assetStore = new ConcurrentHashMap<>();
        AtomicLong idCounter = new AtomicLong();
        when(sequenceGeneratorService.generateSequence(anyString())).thenAnswer(inv -> idCounter.incrementAndGet());
        when(assetRepository.findByVisualizationKey(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(assetStore.get(inv.<String>getArgument(0))));
        when(assetRepository.save(any())).thenAnswer(inv -> {
            VisualizationAsset saved = inv.getArgument(0);
            assetStore.put(saved.getVisualizationKey(), saved);
            return saved;
        });
        when(aiClient.chat(any())).thenReturn(AIResponse.builder().content("{\"imagePrompt\":\"p\"}").build());
        when(imageStorageClient.upload(any(), anyString(), anyString())).thenReturn("https://cdn.example/img.png");

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

        AiArtifactService artifactService = mock(AiArtifactService.class);
        when(artifactService.findReusable(any(), any())).thenReturn(Optional.empty());

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

        service = new ProcessVisualizationService(
                processRepository,
                assetRepository,
                sequenceGeneratorService,
                queueService,
                new AiClientResolver(Map.of("testTextClient", aiClient), Map.of("testImageClient", imageGenerationClient)),
                new ProcessVisualizationPromptBuilder(),
                artifactService,
                new GeneratedImageCodec(),
                consumer,
                new StepVocabularyProvider());
    }

    @Test
    void prepareStepContexts_conditionNodesAreSkipped() {
        Process process = new Process();
        process.setId(1L);
        process.setRecipeId(9L);
        process.setType(ProcessType.MAIN);

        Process.ProcessNode step = stepNode("step-a", "cut", List.of(ingredient("onion", 2, "COUNT", "medium")), "Cut onions", "Chopped onions");
        Process.ProcessNode condition = new Process.ProcessNode();
        condition.setId("cond-1");
        condition.setKind(ProcessNodeKind.CONDITION);
        condition.setData(new LinkedHashMap<>());

        process.setNodes(new ArrayList<>(List.of(step, condition)));
        process.setEdges(new ArrayList<>());
        when(processRepository.findById(1L)).thenReturn(Optional.of(process));

        ProcessVisualizationService.ProcessStepPreparation preparation = service.prepareStepContexts(1L);

        assertEquals(1, preparation.steps().size(), "CONDITION nodes must never be visualized");
        assertEquals("step-a", preparation.steps().get(0).node().getId());
    }

    @Test
    void prepareStepContexts_ordersByEdgesAndChainsPreviousExpectedOutput() {
        Process process = new Process();
        process.setId(2L);
        process.setRecipeId(9L);
        process.setType(ProcessType.MAIN);

        Process.ProcessNode stepA = stepNode("step-a", "cut", List.of(ingredient("onion", 2, "COUNT", "medium")), "Cut onions", "Chopped onions");
        Process.ProcessNode stepB = stepNode("step-b", "fry", List.of(), "Fry the onions", "Golden fried onions");

        // Nodes deliberately stored out of execution order; edges are authoritative.
        process.setNodes(new ArrayList<>(List.of(stepB, stepA)));
        process.setEdges(new ArrayList<>(List.of(edge("step-a", "step-b"))));
        when(processRepository.findById(2L)).thenReturn(Optional.of(process));

        ProcessVisualizationService.ProcessStepPreparation preparation = service.prepareStepContexts(2L);

        assertEquals(List.of("step-a", "step-b"),
                preparation.steps().stream().map(ctx -> ctx.node().getId()).toList());
        assertNull(preparation.steps().get(0).input().previousExpectedOutput());
        assertEquals("Chopped onions", preparation.steps().get(1).input().previousExpectedOutput());
    }

    @Test
    void prepareStepContexts_multipleActionOnIngredients_areAllExtracted() {
        Process process = new Process();
        process.setId(3L);
        process.setRecipeId(9L);
        process.setType(ProcessType.MAIN);

        Process.ProcessNode step = stepNode("step-a", "cut", List.of(
                ingredient("onion", 2, "COUNT", "medium"),
                ingredient("tomato", 3, "COUNT", "large"),
                ingredient("chili", 1, "COUNT", "fine")
        ), "Cut everything", "Prepped vegetables");

        process.setNodes(new ArrayList<>(List.of(step)));
        process.setEdges(new ArrayList<>());
        when(processRepository.findById(3L)).thenReturn(Optional.of(process));

        ProcessVisualizationService.ProcessStepPreparation preparation = service.prepareStepContexts(3L);
        ProcessVisualizationInput input = preparation.steps().get(0).input();

        assertEquals(3, input.ingredients().size());
        assertTrue(input.ingredients().stream().anyMatch(i -> i.name().equals("Onion") && i.preparationStyle().equals("Medium")));
        assertTrue(input.ingredients().stream().anyMatch(i -> i.name().equals("Tomato") && i.preparationStyle().equals("Large")));
        assertTrue(input.ingredients().stream().anyMatch(i -> i.name().equals("Chili") && i.preparationStyle().equals("Fine")));
    }

    @Test
    void prepareStepContexts_subprocessReferences_resolveToNames() {
        Process subprocess = new Process();
        subprocess.setId(77L);
        subprocess.setName("Marinate Chicken");
        subprocess.setType(ProcessType.SUBPROCESS);
        subprocess.setRecipeId(9L);
        when(processRepository.findAllById(any())).thenReturn(List.of(subprocess));

        Process process = new Process();
        process.setId(4L);
        process.setRecipeId(9L);
        process.setType(ProcessType.MAIN);

        Map<String, Object> stepFields = new LinkedHashMap<>();
        stepFields.put("action", "cook");
        stepFields.put("actionDescription", "Cook the marinated chicken");
        stepFields.put("expectedOutput", "Cooked chicken");
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("ingredients", List.of());
        actionOn.put("processes", List.of(Map.of("processId", 77)));
        stepFields.put("actionOn", actionOn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", stepFields);

        Process.ProcessNode step = new Process.ProcessNode();
        step.setId("step-a");
        step.setKind(ProcessNodeKind.STEP);
        step.setData(data);

        process.setNodes(new ArrayList<>(List.of(step)));
        process.setEdges(new ArrayList<>());
        when(processRepository.findById(4L)).thenReturn(Optional.of(process));

        ProcessVisualizationService.ProcessStepPreparation preparation = service.prepareStepContexts(4L);

        assertEquals(List.of("Marinate Chicken"), preparation.steps().get(0).input().subprocessNames());
    }

    @Test
    void prepareStepContexts_processNotFound_throws() {
        when(processRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(RecipeFlowGenerationException.class, () -> service.prepareStepContexts(999L));
    }

    private Process.ProcessNode stepNode(
            String id, String actionId, List<Map<String, Object>> ingredients, String actionDescription, String expectedOutput) {
        Map<String, Object> stepFields = new LinkedHashMap<>();
        stepFields.put("action", actionId);
        stepFields.put("actionDescription", actionDescription);
        stepFields.put("expectedOutput", expectedOutput);
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("ingredients", ingredients);
        actionOn.put("processes", List.of());
        stepFields.put("actionOn", actionOn);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", stepFields);

        Process.ProcessNode node = new Process.ProcessNode();
        node.setId(id);
        node.setKind(ProcessNodeKind.STEP);
        node.setData(data);
        return node;
    }

    private Map<String, Object> ingredient(String ingredientId, int quantity, String unit, String preparationStyleId) {
        Map<String, Object> ingredient = new LinkedHashMap<>();
        ingredient.put("ingredientId", ingredientId);
        ingredient.put("quantity", quantity);
        ingredient.put("unit", unit);
        ingredient.put("preparationStyleId", preparationStyleId);
        return ingredient;
    }

    private Process.ProcessEdge edge(String source, String target) {
        Process.ProcessEdge edge = new Process.ProcessEdge();
        edge.setId(source + "->" + target);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
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
