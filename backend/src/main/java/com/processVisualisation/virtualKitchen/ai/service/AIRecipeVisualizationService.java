package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeVisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeVisualizationStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAssetType;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.utils.VisualizationKeyBuilder;
import com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AIRecipeVisualizationService {

    private static final String RECIPE_STEP_NODE_TYPE = "recipeStepNode";
    private static final Logger logger = LoggerFactory.getLogger(AIRecipeVisualizationService.class);

    private final RecipeRepository recipeRepository;
    private final AIVisualizationAssetRepository AIVisualizationAssetRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final ImageStorageClient imageStorageClient;
    private final AIClient aiClient;
    private final ImageGenerationClient imageGenerationClient;
    private final AIVisualizationPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIRecipeVisualizationService(
            RecipeRepository recipeRepository,
            AIVisualizationAssetRepository AIVisualizationAssetRepository,
            SequenceGeneratorService sequenceGeneratorService,
            AIClient aiClient,
            ImageGenerationClient imageGenerationClient,
            ImageStorageClient imageStorageClient,
            AIVisualizationPromptBuilder promptBuilder
    ) {
        this.recipeRepository = recipeRepository;
        this.AIVisualizationAssetRepository = AIVisualizationAssetRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.aiClient = aiClient;
        this.imageGenerationClient = imageGenerationClient;
        this.imageStorageClient = imageStorageClient;
        this.promptBuilder = promptBuilder;
    }

    public RecipeVisualizationResponseDTO generateVisualization(String recipeId) {
        Optional<Recipe> flowOpt = recipeRepository.findByFlowId(recipeId);
        if (flowOpt.isEmpty()) {
            return RecipeVisualizationResponseDTO.builder()
                    .recipeId(recipeId)
                    .message("Recipe flow not found for recipeId: " + recipeId)
                    .steps(new ArrayList<>())
                    .build();
        }

        Recipe flow = flowOpt.get();
        List<Recipe.NodeDocument> orderedSteps = orderRecipeStepNodes(flow);

        List<RecipeVisualizationStepResponseDTO> results = new ArrayList<>();
        Map<String, Object> previousStepFields = null;

        for (Recipe.NodeDocument node : orderedSteps) {
            Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
            Map<String, Object> stepFields = extractStepFields(data);

            results.add(processStep(node, data, stepFields, previousStepFields));

            previousStepFields = stepFields;
        }

        recipeRepository.save(flow);

        return RecipeVisualizationResponseDTO.builder()
                .recipeId(recipeId)
                .message("Visualization generated")
                .steps(results)
                .build();
    }

    /**
     * Generate (or reuse) the visualization asset for a single recipe step, so the caller can
     * request steps one at a time and reflect progress in the UI as each one completes.
     */
    public RecipeVisualizationStepResponseDTO generateVisualizationForStep(String recipeId, String stepId) {
        Recipe flow = recipeRepository.findByFlowId(recipeId)
                .orElseThrow(() -> new RecipeFlowGenerationException("Recipe flow not found for recipeId: " + recipeId));

        List<Recipe.NodeDocument> orderedSteps = orderRecipeStepNodes(flow);

        int stepIndex = -1;
        for (int i = 0; i < orderedSteps.size(); i++) {
            if (orderedSteps.get(i).getId().equals(stepId)) {
                stepIndex = i;
                break;
            }
        }
        if (stepIndex == -1) {
            throw new RecipeFlowGenerationException("Step not found in recipe flow: " + stepId);
        }

        Recipe.NodeDocument node = orderedSteps.get(stepIndex);
        Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
        Map<String, Object> stepFields = extractStepFields(data);

        Map<String, Object> previousStepFields = null;
        if (stepIndex > 0) {
            Recipe.NodeDocument previousNode = orderedSteps.get(stepIndex - 1);
            Map<String, Object> previousData = previousNode.getData() != null ? previousNode.getData() : new LinkedHashMap<>();
            previousStepFields = extractStepFields(previousData);
        }

        RecipeVisualizationStepResponseDTO result = processStep(node, data, stepFields, previousStepFields);

        recipeRepository.save(flow);

        return result;
    }

    private RecipeVisualizationStepResponseDTO processStep(
            Recipe.NodeDocument node,
            Map<String, Object> data,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
        VisualizationAsset asset = resolveVisualizationAsset(data, stepFields, previousStepFields);

        attachAssetToNode(node, data, asset);

        return RecipeVisualizationStepResponseDTO.builder()
                .stepId(node.getId())
                .visualizationAssetId(asset.getId())
                .imagePrompt(asset.getImagePrompt())
                .imageUrl(asset.getImageUrl())
                .build();
    }

    /**
     * Resolves (finding, reusing, or generating) the {@link VisualizationAsset} for one step.
     * Touches only the {@code VisualizationAsset} collection and the AI/image/storage clients —
     * never the shared {@link Recipe} flow document — so it is safe to run concurrently across
     * steps of the same recipe as a {@link com.processVisualisation.virtualKitchen.common.concurrent.Task}.
     */
    public VisualizationAsset resolveVisualizationAsset(
            Map<String, Object> data,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
        String visualizationKey = VisualizationKeyBuilder.buildFromNodeData(data);
        Optional<VisualizationAsset> existingAsset = AIVisualizationAssetRepository.findByVisualizationKey(visualizationKey);
        VisualizationAsset asset;
        if (existingAsset.isPresent()) {
            asset = existingAsset.get();
            if (asset.getImagePrompt() == null || asset.getImagePrompt().isEmpty()) {
                asset = generatePrompt(visualizationKey, stepFields, previousStepFields);
            }
            if (asset.getImageUrl() == null || asset.getImageUrl().isEmpty()) {
                asset = generateImage(asset);
            }
        } else {
            asset = createAsset(visualizationKey, stepFields, previousStepFields);
        }
        return asset;
    }

    /**
     * Loads the recipe flow and its ordered step contexts once, up front, so a caller (the async
     * job pipeline) can fan the per-step work out across a {@code TaskPool} without every task
     * re-reading the flow document itself. Each step's {@code previousStepFields} come from the
     * preceding node's already-loaded data — not from any other step's generation result — so the
     * returned steps have no execution-order dependency on each other and can run in parallel.
     */
    public RecipeStepPreparation prepareStepContexts(String recipeId) {
        Recipe flow = recipeRepository.findByFlowId(recipeId)
                .orElseThrow(() -> new RecipeFlowGenerationException("Recipe flow not found for recipeId: " + recipeId));

        List<Recipe.NodeDocument> orderedSteps = orderRecipeStepNodes(flow);
        List<StepContext> contexts = new ArrayList<>();
        Map<String, Object> previousStepFields = null;

        for (Recipe.NodeDocument node : orderedSteps) {
            Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
            Map<String, Object> stepFields = extractStepFields(data);
            contexts.add(new StepContext(node, data, stepFields, previousStepFields));
            previousStepFields = stepFields;
        }

        return new RecipeStepPreparation(flow, contexts);
    }

    /**
     * Attaches each successfully generated asset to its node and saves the flow exactly once.
     * This is the ONLY place that mutates/saves the shared flow document for the async pipeline —
     * called after every parallel step task has finished, to avoid the lost-update race that would
     * occur if multiple concurrent tasks each read-mutated-saved the same flow document. Steps
     * missing from {@code assetsByStepId} (failed generations) are left unmutated and can be
     * retried later.
     */
    public void attachResultsAndSave(Recipe flow, Map<String, VisualizationAsset> assetsByStepId) {
        for (Recipe.NodeDocument node : flow.getNodes()) {
            VisualizationAsset asset = assetsByStepId.get(node.getId());
            if (asset != null) {
                Map<String, Object> data = node.getData() != null ? node.getData() : new LinkedHashMap<>();
                attachAssetToNode(node, data, asset);
            }
        }
        recipeRepository.save(flow);
    }

    public record StepContext(
            Recipe.NodeDocument node,
            Map<String, Object> data,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
    }

    public record RecipeStepPreparation(Recipe flow, List<StepContext> steps) {
    }

    private VisualizationAsset generatePrompt(String visualizationKey, Map<String, Object> currentStep, Map<String, Object> previousStep) {
        AIRequest request = AIRequest.builder()
                .systemPrompt(promptBuilder.buildSystemPrompt())
                .userPrompt(promptBuilder.buildUserPrompt(currentStep, previousStep))
                .temperature(0.4d)
                .maxTokens(5000)
                .build();

        logger.info("generating prompt");
        try {
            logger.info("AI Request: {}", objectMapper.writeValueAsString(request));
            AIResponse response = aiClient.chat(request);
            PromptPair prompts = parsePrompts(response == null ? null : response.getContent());
            VisualizationAsset asset = new VisualizationAsset();
            asset.setId(sequenceGeneratorService.generateSequence(VisualizationAsset.SEQUENCE_NAME));
            asset.setVisualizationKey(visualizationKey);
            asset.setType(VisualizationAssetType.ATOMIC);
            asset.setImagePrompt(prompts.imagePrompt());
            asset.setVideoPrompt(prompts.videoPrompt());
            asset.setVideoUrl(null);
            return AIVisualizationAssetRepository.save(asset);
        } catch (Exception e) {
            logger.error("Failed to serialize AIRequest", e);
        }
        return null;
    }

    private VisualizationAsset generateImage(VisualizationAsset asset) {
        String visualizationKey = asset.getVisualizationKey();
        try {
            logger.info("Generating image for visualizationKey: {}, prompt: {}", visualizationKey, asset.getImagePrompt());
            ImageGenerationClient.GeneratedImage generatedImage =
                    imageGenerationClient.generate(asset.getImagePrompt());

            String imagePath = String.format(
                    "visualizations/%s/%s/%s.png",
                    visualizationKey,
                    asset.getId(),
                    UUID.randomUUID()
            );

            String imageUrl = imageStorageClient.upload(
                    generatedImage.data(),
                    generatedImage.mimeType(),
                    imagePath
            );

            asset.setImageUrl(imageUrl);
            logger.info("Successfully generated and uploaded image for visualizationKey: {}, prompt: {}", visualizationKey, asset.getImagePrompt());
        } catch (Exception e) {
            asset.setImageUrl(null);
            logger.error("Failed to generate or upload image for visualizationKey: {}, prompt: {}", visualizationKey, asset.getImagePrompt(), e);
        }
        asset.setVideoUrl(null);
        return AIVisualizationAssetRepository.save(asset);
    }

    private VisualizationAsset createAsset(String visualizationKey, Map<String, Object> currentStep, Map<String, Object> previousStep) {
        VisualizationAsset asset = generatePrompt(visualizationKey, currentStep, previousStep);
        asset = generateImage(asset);
        return AIVisualizationAssetRepository.save(asset);
    }

    private PromptPair parsePrompts(String content) {
        if (!StringUtils.hasText(content)) {
            return new PromptPair("", "");
        }
        try {
            JsonNode root = objectMapper.readTree(stripCodeFences(content));
            String imagePrompt = root.path("imagePrompt").asText("");
            String videoPrompt = root.path("videoPrompt").asText("");
            return new PromptPair(imagePrompt, videoPrompt);
        } catch (Exception ex) {
            logger.warn("Failed to parse visualization prompt JSON, falling back to raw content", ex);
            return new PromptPair(content.trim(), "");
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

    private void attachAssetToNode(Recipe.NodeDocument node, Map<String, Object> data, VisualizationAsset asset) {
        data.put("visualizationAssetId", asset.getId());
        data.put("imagePrompt", asset.getImagePrompt());
        data.put("imageUrl", asset.getImageUrl());
        node.setData(data);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStepFields(Map<String, Object> data) {
        Object nested = data.get("step");
        return nested instanceof Map ? (Map<String, Object>) nested : data;
    }

    /**
     * Orders recipeStepNode nodes by following the whole flow graph's edges (topological order
     * across ALL node types, not just step-to-step edges) instead of relying on array position,
     * so previous-step continuity is accurate. Restricting the graph to step-to-step edges only
     * breaks as soon as two steps are separated by a condition/parallel node: with no direct edge
     * between them both get indegree 0 and the order silently falls back to array position.
     */
    private List<Recipe.NodeDocument> orderRecipeStepNodes(Recipe flow) {
        List<Recipe.NodeDocument> allNodes = flow.getNodes() != null ? flow.getNodes() : new ArrayList<>();

        Set<String> allIds = allNodes.stream()
                .map(Recipe.NodeDocument::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        allIds.forEach(id -> indegree.put(id, 0));

        if (flow.getEdges() != null) {
            for (Recipe.EdgeDocument edge : flow.getEdges()) {
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
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        });

        List<String> orderedIds = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            orderedIds.add(id);
            for (String next : adjacency.getOrDefault(id, List.of())) {
                int nextIndegree = indegree.merge(next, -1, Integer::sum);
                if (nextIndegree <= 0 && !visited.contains(next)) {
                    queue.add(next);
                }
            }
        }

        for (String id : allIds) {
            if (!visited.contains(id)) {
                orderedIds.add(id);
            }
        }

        Map<String, Recipe.NodeDocument> nodeById = allNodes.stream()
                .collect(Collectors.toMap(Recipe.NodeDocument::getId, node -> node, (a, b) -> a));

        List<Recipe.NodeDocument> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Recipe.NodeDocument node = nodeById.get(id);
            if (node != null && RECIPE_STEP_NODE_TYPE.equals(node.getType())) {
                ordered.add(node);
            }
        }
        return ordered;
    }

    private record PromptPair(String imagePrompt, String videoPrompt) {
    }
}
