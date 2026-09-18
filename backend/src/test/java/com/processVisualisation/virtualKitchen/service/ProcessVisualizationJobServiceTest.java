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
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationJobService;
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.StepVocabularyProvider;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationImageArtifactConsumer;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
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

class ProcessVisualizationJobServiceTest {

    private ProcessRepository processRepository;
    private VisualizationJobRepository visualizationJobRepository;
    private MongoTemplate mongoTemplate;
    private ImageGenerationClient imageGenerationClient;
    private ProcessVisualizationJobService service;

    @BeforeEach
    void setUp() {
        processRepository = mock(ProcessRepository.class);
        AIVisualizationAssetRepository assetRepository = mock(AIVisualizationAssetRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        AIClient aiClient = mock(AIClient.class);
        imageGenerationClient = mock(ImageGenerationClient.class);
        ImageStorageClient imageStorageClient = mock(ImageStorageClient.class);

        AtomicLong idCounter = new AtomicLong(0);
        when(sequenceGeneratorService.generateSequence(anyString())).thenAnswer(inv -> idCounter.incrementAndGet());
        Map<String, VisualizationAsset> assetStore = new ConcurrentHashMap<>();
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
        ModelSelectionService modelSelectionService = new ModelSelectionService(modelRegistry, creditService);
        AiClientResolver clientResolver = new AiClientResolver(
                Map.of("testTextClient", aiClient), Map.of("testImageClient", imageGenerationClient));

        AiRequestJobRepository aiRequestJobRepository = mock(AiRequestJobRepository.class);
        when(aiRequestJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

        ProcessVisualizationService processVisualizationService = new ProcessVisualizationService(
                processRepository,
                assetRepository,
                sequenceGeneratorService,
                queueService,
                clientResolver,
                new ProcessVisualizationPromptBuilder(),
                artifactService,
                new GeneratedImageCodec(),
                imageArtifactConsumer,
                new StepVocabularyProvider());

        visualizationJobRepository = mock(VisualizationJobRepository.class);
        when(visualizationJobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mongoTemplate = mock(MongoTemplate.class);

        service = new ProcessVisualizationJobService(
                processVisualizationService,
                visualizationJobRepository,
                mongoTemplate,
                new ThreadPoolTaskPool(2, "test-process-visualization"),
                new ThreadPoolTaskPool(1, "test-process-orchestrator"));
    }

    @Test
    void startJob_happyPath_savesProcessExactlyOnceAndRecordsEveryStep() throws Exception {
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1, 2, 3}));
        Process process = processWithSteps(1L, 3);
        when(processRepository.findById(1L)).thenReturn(Optional.of(process));

        VisualizationJobResponseDTO started = service.startJob(1L, 100L, 1L);
        assertEquals(3, started.getTotalSteps());
        assertEquals("QUEUED", started.getStatus());
        assertEquals(1L, started.getProcessId());

        // Only the reduce step saves the shared process document, and it must happen exactly once
        // regardless of how many steps ran concurrently.
        verify(processRepository, timeout(5000).times(1)).save(process);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(4))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(com.processVisualisation.virtualKitchen.ai.model.VisualizationJob.class));

        List<Update> updates = updateCaptor.getAllValues();
        long stepIncrementCount = updates.stream().filter(u -> incValue(u, "completedSteps") == 1).count();
        assertEquals(3, stepIncrementCount, "expected exactly one completedSteps increment per step");

        boolean sawCompleted = updates.stream().anyMatch(u -> statusValue(u) == VisualizationJobStatus.COMPLETED);
        assertTrue(sawCompleted);
    }

    @Test
    void startJob_partialFailure_stillSavesOnceAndMarksCompletedWithErrors() throws Exception {
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1}))
                .thenThrow(new RuntimeException("image generation failed"))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1}));

        Process process = processWithSteps(2L, 3);
        when(processRepository.findById(2L)).thenReturn(Optional.of(process));

        service.startJob(1L, 100L, 2L);

        verify(processRepository, timeout(5000).times(1)).save(process);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, timeout(5000).atLeast(4))
                .updateFirst(any(Query.class), updateCaptor.capture(), eq(com.processVisualisation.virtualKitchen.ai.model.VisualizationJob.class));

        boolean sawCompletedWithErrors = updateCaptor.getAllValues().stream()
                .anyMatch(u -> statusValue(u) == VisualizationJobStatus.COMPLETED_WITH_ERRORS);
        assertTrue(sawCompletedWithErrors);
    }

    @Test
    void startJob_processNotFound_throwsSynchronouslyWithoutCreatingAJob() {
        when(processRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RecipeFlowGenerationException.class, () -> service.startJob(1L, 100L, 999L));

        verify(visualizationJobRepository, never()).save(any());
    }

    /**
     * Visualizing a MAIN process must only touch its own steps and its own document — a step's
     * Action On referencing a SUBPROCESS must never cause that subprocess's own document to be
     * loaded (beyond resolving its name for prompt context) or saved.
     */
    @Test
    void startJob_stepReferencingSubprocess_neverTouchesTheSubprocessDocument() throws Exception {
        when(imageGenerationClient.generate(anyString()))
                .thenReturn(new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1}));

        Process subprocess = new Process();
        subprocess.setId(50L);
        subprocess.setName("Marinate Chicken");
        subprocess.setType(ProcessType.SUBPROCESS);
        when(processRepository.findAllById(any())).thenReturn(List.of(subprocess));

        Process main = new Process();
        main.setId(3L);
        main.setType(ProcessType.MAIN);
        Map<String, Object> stepFields = new LinkedHashMap<>();
        stepFields.put("action", "cook");
        stepFields.put("actionDescription", "Cook it");
        stepFields.put("expectedOutput", "Cooked");
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("ingredients", List.of());
        actionOn.put("processes", List.of(Map.of("processId", 50)));
        stepFields.put("actionOn", actionOn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", stepFields);
        Process.ProcessNode step = new Process.ProcessNode();
        step.setId("step-a");
        step.setKind(ProcessNodeKind.STEP);
        step.setData(data);
        main.setNodes(new ArrayList<>(List.of(step)));
        main.setEdges(new ArrayList<>());
        when(processRepository.findById(3L)).thenReturn(Optional.of(main));

        service.startJob(1L, 100L, 3L);

        verify(processRepository, timeout(5000).times(1)).save(main);
        verify(processRepository, never()).save(subprocess);
        verify(processRepository, never()).findById(50L);
    }

    private Process processWithSteps(Long id, int count) {
        Process process = new Process();
        process.setId(id);
        process.setType(ProcessType.MAIN);
        List<Process.ProcessNode> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> stepFields = new LinkedHashMap<>();
            stepFields.put("action", "cut");
            stepFields.put("actionDescription", "Step " + i);
            stepFields.put("expectedOutput", "Result " + i);
            Map<String, Object> actionOn = new LinkedHashMap<>();
            actionOn.put("ingredients", List.of());
            actionOn.put("processes", List.of());
            stepFields.put("actionOn", actionOn);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("step", stepFields);

            Process.ProcessNode node = new Process.ProcessNode();
            node.setId("node-" + i);
            node.setKind(ProcessNodeKind.STEP);
            node.setData(data);
            nodes.add(node);
        }
        process.setNodes(nodes);
        process.setEdges(new ArrayList<>());
        return process;
    }

    private int incValue(Update update, String field) {
        Document updateObject = update.getUpdateObject();
        Document inc = (Document) updateObject.get("$inc");
        if (inc == null || !inc.containsKey(field)) return 0;
        return ((Number) inc.get(field)).intValue();
    }

    private VisualizationJobStatus statusValue(Update update) {
        Document updateObject = update.getUpdateObject();
        Document set = (Document) updateObject.get("$set");
        if (set == null || !set.containsKey("status")) return null;
        return (VisualizationJobStatus) set.get("status");
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
