package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactKeyBuilder;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactSpec;
import com.processVisualisation.virtualKitchen.ai.artifact.codec.GeneratedImageCodec;
import com.processVisualisation.virtualKitchen.ai.dispatch.AiClientResolver;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestOutcome;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestQueueService;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAssetType;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.exception.RecipeProcessAiException;
import com.processVisualisation.virtualKitchen.common.utils.VisualizationKeyBuilder;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates AI-driven image visualization assets for a Process model's own STEP nodes (CONDITION
 * nodes are never visualized): a {@link VisualizationAsset} lookup-fill-or-create lifecycle with
 * durable-artifact-before-upload sequencing via {@link AiArtifactService}/
 * {@link VisualizationImageArtifactConsumer}, reading each step's Action On (ingredients +
 * subprocess references). Scoped to one {@link Process} (a MAIN or a
 * SUBPROCESS) at a time: visualizing a process only visualizes its own steps, never a
 * subprocess referenced from one of them (see class javadoc on {@link Process} — a subprocess's
 * name already stands in for its summary/output, so no recursive walk is needed for V1).
 */
@Service
public class RecipeProcessVisualizationService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeProcessVisualizationService.class);

    private static final Map<String, String> UNIT_LABELS = Map.of(
            "COUNT", "", "GRAM", "g", "KG", "kg", "ML", "ml", "LITER", "l");

    private final ProcessRepository processRepository;
    private final AIVisualizationAssetRepository assetRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final AiRequestQueueService queueService;
    private final AiClientResolver clientResolver;
    private final RecipeProcessVisualizationPromptBuilder promptBuilder;
    private final AiArtifactService artifactService;
    private final GeneratedImageCodec generatedImageCodec;
    private final VisualizationImageArtifactConsumer imageArtifactConsumer;
    private final RecipeStepVocabularyProvider vocabularyProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeProcessVisualizationService(
            ProcessRepository processRepository,
            AIVisualizationAssetRepository assetRepository,
            SequenceGeneratorService sequenceGeneratorService,
            AiRequestQueueService queueService,
            AiClientResolver clientResolver,
            RecipeProcessVisualizationPromptBuilder promptBuilder,
            AiArtifactService artifactService,
            GeneratedImageCodec generatedImageCodec,
            VisualizationImageArtifactConsumer imageArtifactConsumer,
            RecipeStepVocabularyProvider vocabularyProvider
    ) {
        this.processRepository = processRepository;
        this.assetRepository = assetRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.queueService = queueService;
        this.clientResolver = clientResolver;
        this.promptBuilder = promptBuilder;
        this.artifactService = artifactService;
        this.generatedImageCodec = generatedImageCodec;
        this.imageArtifactConsumer = imageArtifactConsumer;
        this.vocabularyProvider = vocabularyProvider;
    }

    /**
     * Loads the process and its ordered, STEP-only visualization inputs once, up front, so the
     * async job pipeline can fan the per-step work out across a {@code TaskPool} without every
     * task re-reading the process document itself.
     *
     * @param processId the process (MAIN or SUBPROCESS) to prepare
     * @throws RecipeProcessAiException if the process cannot be found
     */
    public ProcessStepPreparation prepareStepContexts(Long processId) {
        Process process = processRepository.findById(processId)
                .orElseThrow(() -> new RecipeProcessAiException("Process not found: " + processId));

        List<Process.ProcessNode> orderedSteps = orderStepNodes(process);
        Map<Long, String> subprocessNameById = resolveSubprocessNames(orderedSteps);

        List<StepContext> contexts = new ArrayList<>();
        String previousExpectedOutput = null;
        for (Process.ProcessNode node : orderedSteps) {
            Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
            RecipeProcessVisualizationInput input = extractVisualizationInput(data, subprocessNameById, previousExpectedOutput);
            contexts.add(new StepContext(node, input));
            previousExpectedOutput = input.expectedOutput();
        }

        return new ProcessStepPreparation(process, contexts);
    }

    /**
     * Resolves (finding, reusing, or generating) the {@link VisualizationAsset} for one step.
     * Touches only the {@code VisualizationAsset} collection and the AI/image/storage clients —
     * never the shared {@link Process} document — so it is safe to run concurrently across steps
     * of the same process.
     * <p>
     * The cache/dedup key stays the simple {@code recipeId::stepId} shape (V1 decision — no
     * cross-recipe/semantic reuse yet): two steps never share an asset, even across processes,
     * since {@code stepId} (a generated node id) is already globally unique.
     */
    public VisualizationAsset resolveVisualizationAsset(Long userId, Long recipeId, String stepId, RecipeProcessVisualizationInput input) {
        String visualizationKey = VisualizationKeyBuilder.build(String.valueOf(recipeId), stepId);
        Optional<VisualizationAsset> existingAsset = assetRepository.findByVisualizationKey(visualizationKey);
        VisualizationAsset asset;
        if (existingAsset.isPresent()) {
            asset = existingAsset.get();
            if (asset.getImagePrompt() == null || asset.getImagePrompt().isEmpty()) {
                asset = generatePrompt(userId, visualizationKey, input);
            }
            requirePrompted(asset, visualizationKey);
            if (asset.getImageUrl() == null || asset.getImageUrl().isEmpty()) {
                asset = generateImage(userId, asset);
            }
        } else {
            asset = createAsset(userId, visualizationKey, input);
        }
        return asset;
    }

    /**
     * Attaches each successfully generated asset to its STEP node and saves the process exactly
     * once — the only place that mutates/saves the shared process document for the async pipeline,
     * to avoid a lost-update race across concurrently-completing step tasks. Steps missing from
     * {@code assetsByStepId} (failed generations) are left unmutated and can be retried later.
     */
    public void attachResultsAndSave(Process process, Map<String, VisualizationAsset> assetsByStepId) {
        for (Process.ProcessNode node : process.getNodes()) {
            VisualizationAsset asset = assetsByStepId.get(node.getId());
            if (asset != null) {
                Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
                data.put("visualizationAssetId", asset.getId());
                data.put("imagePrompt", asset.getImagePrompt());
                data.put("imageUrl", asset.getImageUrl());
                node.setData(data);
            }
        }
        processRepository.save(process);
    }

    /** One STEP's pre-loaded generation context: its process node and extracted visualization input. */
    public record StepContext(Process.ProcessNode node, RecipeProcessVisualizationInput input) {
    }

    /** The result of {@link #prepareStepContexts}: the loaded process with its ordered step contexts. */
    public record ProcessStepPreparation(Process process, List<StepContext> steps) {
    }

    private VisualizationAsset generatePrompt(Long userId, String visualizationKey, RecipeProcessVisualizationInput input) {
        try {
            AiRequestOutcome<String> outcome = queueService.executeInline(
                    userId,
                    AiCapability.TEXT_TO_TEXT,
                    null,
                    null,
                    "process-visualization-prompt",
                    visualizationKey,
                    selection -> {
                        AIClient client = clientResolver.resolveTextClient(selection.model());
                        AIRequest request = AIRequest.builder()
                                .model(selection.model().getProviderModelId())
                                .systemPrompt(promptBuilder.buildSystemPrompt())
                                .userPrompt(promptBuilder.buildUserPrompt(input))
                                .temperature(0.4d)
                                .maxTokens(2000)
                                .build();
                        AIResponse response = client.chat(request);
                        return parseImagePrompt(response == null ? null : response.getContent());
                    }
            );

            VisualizationAsset asset = new VisualizationAsset();
            asset.setId(sequenceGeneratorService.generateSequence(VisualizationAsset.SEQUENCE_NAME));
            asset.setVisualizationKey(visualizationKey);
            asset.setType(VisualizationAssetType.ATOMIC);
            asset.setImagePrompt(outcome.value());
            asset.setVideoPrompt(null);
            asset.setVideoUrl(null);
            return assetRepository.save(asset);
        } catch (Exception e) {
            logger.error("Failed to generate process visualization prompt for key {}", visualizationKey, e);
        }
        return null;
    }

    /**
     * Generates this step's image and places it in object storage, in two separable stages so a
     * storage failure cannot destroy a paid generation — the provider's bytes are staged as an
     * {@code AiArtifact} (inside {@code executeInline}) before the upload runs, and the artifact is
     * only retired once the upload succeeds, so a retry reuses the already-generated image instead
     * of paying for a new one. Reuses {@link VisualizationImageArtifactConsumer} unchanged, since it
     * only depends on the (generic) {@code visualizationKey}, not on which model produced the asset.
     */
    private VisualizationAsset generateImage(Long userId, VisualizationAsset asset) {
        String visualizationKey = asset.getVisualizationKey();
        try {
            AiArtifactSpec<ImageGenerationClient.GeneratedImage> artifactSpec = AiArtifactSpec.of(
                    AiArtifactKeyBuilder.build(
                            AiCapability.TEXT_TO_IMAGE,
                            "process-visualization-image",
                            visualizationKey,
                            asset.getImagePrompt()),
                    generatedImageCodec,
                    VisualizationImageArtifactConsumer.CONSUMER_ID
            );

            AiRequestOutcome<ImageGenerationClient.GeneratedImage> outcome = queueService.executeInline(
                    userId,
                    AiCapability.TEXT_TO_IMAGE,
                    null,
                    null,
                    "process-visualization-image",
                    visualizationKey,
                    artifactSpec,
                    selection -> clientResolver.resolveImageClient(selection.model()).generate(asset.getImagePrompt())
            );

            // The payload is durable from here on, whether it was just generated or reused.
            AiArtifact artifact = artifactOrTransient(outcome, visualizationKey);
            String imageUrl = imageArtifactConsumer.consume(artifact, outcome.value());
            artifactService.markConsumed(artifact.getId());

            ModelSelectionOutcome selection = outcome.selection();
            asset.setImageUrl(imageUrl);
            asset.setImageFailureReason(null);
            asset.setResolvedModelKey(selection.model().getKey());
            asset.setResolvedTier(selection.model().getTier());
            asset.setUsedFallback(selection.usedFallback());
        } catch (Exception e) {
            asset.setImageUrl(null);
            asset.setImageFailureReason(describeFailure(e));
            logger.error("Failed to generate or upload image for visualizationKey: {} "
                            + "(any generated payload is retained for recovery)", visualizationKey, e);
        }
        asset.setVideoUrl(null);
        return assetRepository.save(asset);
    }

    private VisualizationAsset createAsset(Long userId, String visualizationKey, RecipeProcessVisualizationInput input) {
        VisualizationAsset asset = generatePrompt(userId, visualizationKey, input);
        requirePrompted(asset, visualizationKey);
        return generateImage(userId, asset);
    }

    private AiArtifact artifactOrTransient(
            AiRequestOutcome<ImageGenerationClient.GeneratedImage> outcome, String visualizationKey) {
        if (outcome.artifact() != null) {
            return outcome.artifact();
        }
        ModelSelectionOutcome selection = outcome.selection();
        AiArtifact transientArtifact = new AiArtifact();
        transientArtifact.setCorrelationId(visualizationKey);
        transientArtifact.setCorrelationType("process-visualization-image");
        transientArtifact.setCapability(AiCapability.TEXT_TO_IMAGE);
        transientArtifact.setProducedByModelKey(selection.model().getKey());
        transientArtifact.setProducedByTier(selection.model().getTier());
        transientArtifact.setUsedFallback(selection.usedFallback());
        return transientArtifact;
    }

    private String describeFailure(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message)
                ? e.getClass().getSimpleName() + ": " + message
                : e.getClass().getSimpleName();
    }

    private void requirePrompted(VisualizationAsset asset, String visualizationKey) {
        if (asset == null) {
            throw new RecipeProcessAiException(
                    "Could not generate a visualization prompt for step: " + visualizationKey);
        }
    }

    private String parseImagePrompt(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(content));
            return root.path("imagePrompt").asText("");
        } catch (Exception ex) {
            logger.warn("Failed to parse process visualization prompt JSON, falling back to raw content", ex);
            return content.trim();
        }
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstNewLine = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewLine >= 0 && lastFence > firstNewLine) {
                return trimmed.substring(firstNewLine + 1, lastFence).trim();
            }
        }
        return content;
    }

    /**
     * Orders STEP nodes by following the whole process graph's edges (topological order across ALL
     * node kinds, not just STEP-to-STEP edges) instead of relying on array position, so previous-step
     * continuity is accurate: two STEPs separated by a CONDITION
     * node would otherwise both get indegree 0 and silently fall back to array position.
     */
    private List<Process.ProcessNode> orderStepNodes(Process process) {
        List<Process.ProcessNode> allNodes = process.getNodes() != null ? process.getNodes() : new ArrayList<>();

        Set<String> allIds = allNodes.stream()
                .map(Process.ProcessNode::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        allIds.forEach(id -> indegree.put(id, 0));

        if (process.getEdges() != null) {
            for (Process.ProcessEdge edge : process.getEdges()) {
                String source = edge.getSource();
                String target = edge.getTarget();
                if (allIds.contains(source) && allIds.contains(target)) {
                    adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                    indegree.merge(target, 1, Integer::sum);
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        allIds.forEach(id -> {
            if (indegree.get(id) == 0) queue.add(id);
        });

        List<String> orderedIds = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) continue;
            orderedIds.add(id);
            for (String next : adjacency.getOrDefault(id, List.of())) {
                int nextIndegree = indegree.merge(next, -1, Integer::sum);
                if (nextIndegree <= 0 && !visited.contains(next)) queue.add(next);
            }
        }

        for (String id : allIds) {
            if (!visited.contains(id)) orderedIds.add(id);
        }

        Map<String, Process.ProcessNode> nodeById = allNodes.stream()
                .collect(Collectors.toMap(Process.ProcessNode::getId, node -> node, (a, b) -> a));

        List<Process.ProcessNode> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Process.ProcessNode node = nodeById.get(id);
            if (node != null && node.getKind() == ProcessNodeKind.STEP) {
                ordered.add(node);
            }
        }
        return ordered;
    }

    /** Batch-resolves every subprocess name referenced by any step's Action On, in one query. */
    private Map<Long, String> resolveSubprocessNames(List<Process.ProcessNode> stepNodes) {
        Set<Long> referencedIds = new LinkedHashSet<>();
        for (Process.ProcessNode node : stepNodes) {
            Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
            for (Long processId : extractSubprocessIds(extractStepFields(data))) {
                referencedIds.add(processId);
            }
        }
        if (referencedIds.isEmpty()) {
            return Map.of();
        }
        return processRepository.findAllById(referencedIds).stream()
                .collect(Collectors.toMap(Process::getId, Process::getName, (a, b) -> a));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStepFields(Map<String, Object> data) {
        Object nested = data.get("step");
        return nested instanceof Map ? (Map<String, Object>) nested : data;
    }

    private RecipeProcessVisualizationInput extractVisualizationInput(
            Map<String, Object> data, Map<Long, String> subprocessNameById, String previousExpectedOutput) {
        Map<String, Object> step = extractStepFields(data);

        String actionId = asString(step.get("action"));
        String action = "custom".equals(actionId)
                ? orBlank(asString(step.get("customActionName")))
                : vocabularyProvider.actionLabel(actionId);

        List<RecipeProcessVisualizationInput.IngredientTarget> ingredients = extractIngredients(step);
        List<String> subprocessNames = extractSubprocessIds(step).stream()
                .map(id -> subprocessNameById.getOrDefault(id, "a prepared subprocess"))
                .toList();

        String flameLevelId = asString(step.get("flameLevelId"));
        String flameLevel = "custom".equals(flameLevelId)
                ? asString(step.get("customFlameLevel"))
                : (StringUtils.hasText(flameLevelId) ? vocabularyProvider.flameLevelLabel(flameLevelId) : "");

        String durationValue = asString(step.get("durationValue"));
        String durationUnit = asString(step.get("durationUnit"));
        String duration = StringUtils.hasText(durationValue) && StringUtils.hasText(durationUnit)
                ? durationValue + " " + durationUnit
                : "";

        return new RecipeProcessVisualizationInput(
                action,
                ingredients,
                subprocessNames,
                asString(step.get("actionDescription")),
                asString(step.get("expectedOutput")),
                asString(step.get("temperature")),
                flameLevel,
                duration,
                previousExpectedOutput
        );
    }

    @SuppressWarnings("unchecked")
    private List<RecipeProcessVisualizationInput.IngredientTarget> extractIngredients(Map<String, Object> step) {
        Object actionOn = step.get("actionOn");
        if (!(actionOn instanceof Map)) return List.of();
        Object rawIngredients = ((Map<String, Object>) actionOn).get("ingredients");
        if (!(rawIngredients instanceof List)) return List.of();

        List<RecipeProcessVisualizationInput.IngredientTarget> targets = new ArrayList<>();
        for (Object entry : (List<Object>) rawIngredients) {
            if (!(entry instanceof Map)) continue;
            Map<String, Object> ingredient = (Map<String, Object>) entry;

            String ingredientId = asString(ingredient.get("ingredientId"));
            if (!StringUtils.hasText(ingredientId)) continue;
            String name = vocabularyProvider.ingredientLabel(ingredientId);

            String quantity = formatQuantity(ingredient.get("quantity"));
            String unit = UNIT_LABELS.getOrDefault(asString(ingredient.get("unit")), "");

            String preparationStyleId = asString(ingredient.get("preparationStyleId"));
            String preparationStyle = "custom".equals(preparationStyleId)
                    ? asString(ingredient.get("customPreparationStyle"))
                    : (StringUtils.hasText(preparationStyleId) ? vocabularyProvider.preparationStyleLabel(preparationStyleId) : "");

            targets.add(new RecipeProcessVisualizationInput.IngredientTarget(name, quantity, unit, preparationStyle));
        }
        return targets;
    }

    @SuppressWarnings("unchecked")
    private List<Long> extractSubprocessIds(Map<String, Object> step) {
        Object actionOn = step.get("actionOn");
        if (!(actionOn instanceof Map)) return List.of();
        Object rawProcesses = ((Map<String, Object>) actionOn).get("processes");
        if (!(rawProcesses instanceof List)) return List.of();

        List<Long> ids = new ArrayList<>();
        for (Object entry : (List<Object>) rawProcesses) {
            if (!(entry instanceof Map)) continue;
            Object processId = ((Map<String, Object>) entry).get("processId");
            if (processId instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String orBlank(String value) {
        return value == null ? "" : value;
    }

    private String formatQuantity(Object value) {
        if (!(value instanceof Number number)) return "";
        double doubleValue = number.doubleValue();
        return doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)
                ? String.valueOf((long) doubleValue)
                : String.valueOf(doubleValue);
    }
}
