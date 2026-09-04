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

    private final RecipeRepository flowRepository;
    private final AIVisualizationAssetRepository AIVisualizationAssetRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final ImageStorageClient imageStorageClient;
    private final AIClient aiClient;
    private final ImageGenerationClient imageGenerationClient;
    private final AIVisualizationPromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIRecipeVisualizationService(
            RecipeRepository flowRepository,
            AIVisualizationAssetRepository AIVisualizationAssetRepository,
            SequenceGeneratorService sequenceGeneratorService,
            AIClient aiClient,
            ImageGenerationClient imageGenerationClient,
            ImageStorageClient imageStorageClient,
            AIVisualizationPromptBuilder promptBuilder
    ) {
        this.flowRepository = flowRepository;
        this.AIVisualizationAssetRepository = AIVisualizationAssetRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.aiClient = aiClient;
        this.imageGenerationClient = imageGenerationClient;
        this.imageStorageClient = imageStorageClient;
        this.promptBuilder = promptBuilder;
    }

    public RecipeVisualizationResponseDTO generateVisualization(String recipeId) {
        Optional<Recipe> flowOpt = flowRepository.findByFlowId(recipeId);
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

            String visualizationKey = VisualizationKeyBuilder.buildFromNodeData(data);
            Map<String, Object> previousStepFieldsForLambda = previousStepFields;
            Optional<VisualizationAsset> existingAsset = AIVisualizationAssetRepository.findByVisualizationKey(visualizationKey);
            VisualizationAsset asset;
            if (existingAsset.isPresent()) {
                asset = existingAsset.get();
                if (asset.getImagePrompt() == null || asset.getImagePrompt().isEmpty()) {
                    asset = generatePrompt(visualizationKey, stepFields, previousStepFieldsForLambda);
                }
                if (asset.getImageUrl() == null || asset.getImageUrl().isEmpty()) {
                    asset = generateImage(asset);
                }
            } else {
                asset = createAsset(visualizationKey, stepFields, previousStepFieldsForLambda);
            }

            attachAssetToNode(node, data, asset);

            results.add(RecipeVisualizationStepResponseDTO.builder()
                    .stepId(node.getId())
                    .visualizationAssetId(asset.getId())
                    .imagePrompt(asset.getImagePrompt())
                    .imageUrl(asset.getImageUrl())
                    .build());

            previousStepFields = stepFields;
        }

        flowRepository.save(flow);

        return RecipeVisualizationResponseDTO.builder()
                .recipeId(recipeId)
                .message("Visualization generated")
                .steps(results)
                .build();
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
     * Orders recipeStepNode nodes by following the flow's edges (topological order)
     * instead of relying on array position, so previous-step continuity is accurate.
     */
    private List<Recipe.NodeDocument> orderRecipeStepNodes(Recipe flow) {
        List<Recipe.NodeDocument> recipeStepNodes = flow.getNodes().stream()
                .filter(node -> RECIPE_STEP_NODE_TYPE.equals(node.getType()))
                .collect(Collectors.toList());

        Set<String> stepIds = recipeStepNodes.stream()
                .map(Recipe.NodeDocument::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        stepIds.forEach(id -> indegree.put(id, 0));

        if (flow.getEdges() != null) {
            for (Recipe.EdgeDocument edge : flow.getEdges()) {
                String source = edge.getSource();
                String target = edge.getTarget();
                if (stepIds.contains(source) && stepIds.contains(target)) {
                    adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                    indegree.merge(target, 1, Integer::sum);
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        stepIds.forEach(id -> {
            if (indegree.get(id) == 0) {
                queue.add(id);
            }
        });

        List<String> orderedIds = new ArrayList<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            orderedIds.add(id);
            for (String next : adjacency.getOrDefault(id, List.of())) {
                indegree.merge(next, -1, Integer::sum);
                if (indegree.get(next) == 0) {
                    queue.add(next);
                }
            }
        }

        for (String id : stepIds) {
            if (!orderedIds.contains(id)) {
                orderedIds.add(id);
            }
        }

        Map<String, Recipe.NodeDocument> nodeById = recipeStepNodes.stream()
                .collect(Collectors.toMap(Recipe.NodeDocument::getId, node -> node, (a, b) -> a));

        List<Recipe.NodeDocument> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Recipe.NodeDocument node = nodeById.get(id);
            if (node != null) {
                ordered.add(node);
            }
        }
        return ordered;
    }

    private record PromptPair(String imagePrompt, String videoPrompt) {
    }
}

