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
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
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

/**
 * Generates AI-driven image/prompt visualization assets for a recipe flow's
 * steps. For each {@code recipeStepNode}, resolves (finds, completes, or
 * creates) a {@link VisualizationAsset} via {@link AIVisualizationPromptBuilder}
 * and the AI/image-generation/storage clients, then attaches the result back
 * onto the flow's node data. Exposes both a synchronous, whole-flow entry
 * point and step-scoped operations ({@link #resolveVisualizationAsset},
 * {@link #prepareStepContexts}, {@link #attachResultsAndSave}) designed to be
 * driven concurrently by {@link VisualizationJobService}'s task pool.
 */
@Service
public class AIRecipeVisualizationService {

    private static final String RECIPE_STEP_NODE_TYPE = "recipeStepNode";
    private static final Logger logger = LoggerFactory.getLogger(AIRecipeVisualizationService.class);

    private final RecipeRepository recipeRepository;
    private final AIVisualizationAssetRepository AIVisualizationAssetRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final AiRequestQueueService queueService;
    private final AiClientResolver clientResolver;
    private final AIVisualizationPromptBuilder promptBuilder;
    private final AiArtifactService artifactService;
    private final GeneratedImageCodec generatedImageCodec;
    private final VisualizationImageArtifactConsumer imageArtifactConsumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIRecipeVisualizationService(
            RecipeRepository recipeRepository,
            AIVisualizationAssetRepository AIVisualizationAssetRepository,
            SequenceGeneratorService sequenceGeneratorService,
            AiRequestQueueService queueService,
            AiClientResolver clientResolver,
            AIVisualizationPromptBuilder promptBuilder,
            AiArtifactService artifactService,
            GeneratedImageCodec generatedImageCodec,
            VisualizationImageArtifactConsumer imageArtifactConsumer
    ) {
        this.recipeRepository = recipeRepository;
        this.AIVisualizationAssetRepository = AIVisualizationAssetRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.queueService = queueService;
        this.clientResolver = clientResolver;
        this.promptBuilder = promptBuilder;
        this.artifactService = artifactService;
        this.generatedImageCodec = generatedImageCodec;
        this.imageArtifactConsumer = imageArtifactConsumer;
    }

    /**
     * Synchronously generates (or reuses) visualization assets for every
     * recipe step of the given flow, in execution order, and persists the
     * updated flow with the assets attached.
     *
     * @param userId the id of the user this generation's credit charges belong to
     * @param recipeId identifier of the recipe flow to visualize
     * @return the visualization response with one result per step, or a
     *         message-only response with an empty step list if the flow is not found
     */
    public RecipeVisualizationResponseDTO generateVisualization(Long userId, String recipeId) {
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

            results.add(processStep(userId, recipeId, node, data, stepFields, previousStepFields));

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
     *
     * @param userId the id of the user this generation's credit charges belong to
     * @param recipeId identifier of the recipe flow containing the step
     * @param stepId identifier of the specific step node to visualize
     * @return the visualization result for the requested step
     * @throws RecipeFlowGenerationException if the recipe flow or the step id
     *         cannot be found within it
     */
    public RecipeVisualizationStepResponseDTO generateVisualizationForStep(Long userId, String recipeId, String stepId) {
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

        RecipeVisualizationStepResponseDTO result = processStep(userId, recipeId, node, data, stepFields, previousStepFields);

        recipeRepository.save(flow);

        return result;
    }

    private RecipeVisualizationStepResponseDTO processStep(
            Long userId,
            String recipeId,
            Recipe.NodeDocument node,
            Map<String, Object> data,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
        VisualizationAsset asset = resolveVisualizationAsset(userId, recipeId, node.getId(), stepFields, previousStepFields);

        attachAssetToNode(node, data, asset);

        return RecipeVisualizationStepResponseDTO.builder()
                .stepId(node.getId())
                .visualizationAssetId(asset.getId())
                .imagePrompt(asset.getImagePrompt())
                .imageUrl(asset.getImageUrl())
                .modelKey(asset.getResolvedModelKey())
                .modelTier(asset.getResolvedTier())
                .usedFallback(asset.isUsedFallback())
                .build();
    }

    /**
     * Resolves (finding, reusing, or generating) the {@link VisualizationAsset} for one step.
     * Touches only the {@code VisualizationAsset} collection and the AI/image/storage clients —
     * never the shared {@link Recipe} flow document — so it is safe to run concurrently across
     * steps of the same recipe as a {@link com.processVisualisation.virtualKitchen.common.concurrent.Task}.
     * <p>
     * The cache/dedup key is scoped to exactly this step of exactly this recipe
     * ({@code recipeId::stepId} via {@link VisualizationKeyBuilder}) — two steps never share an
     * asset, even if their content looks identical or is sparse/blank. Re-visualizing the same
     * step of the same recipe is the only case that reuses an existing asset.
     *
     * @param userId the id of the user this generation's credit charges belong to
     * @param recipeId the recipe flow's id
     * @param stepId the step node's id within that recipe
     * @param stepFields the current step's extracted fields used for prompt generation
     * @param previousStepFields the preceding step's extracted fields, or {@code null} if none
     * @return the resolved (existing or newly generated) visualization asset
     */
    public VisualizationAsset resolveVisualizationAsset(
            Long userId,
            String recipeId,
            String stepId,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
        String visualizationKey = VisualizationKeyBuilder.build(recipeId, stepId);
        Optional<VisualizationAsset> existingAsset = AIVisualizationAssetRepository.findByVisualizationKey(visualizationKey);
        VisualizationAsset asset;
        if (existingAsset.isPresent()) {
            asset = existingAsset.get();
            if (asset.getImagePrompt() == null || asset.getImagePrompt().isEmpty()) {
                asset = generatePrompt(userId, visualizationKey, stepFields, previousStepFields);
            }
            requirePrompted(asset, visualizationKey);
            if (asset.getImageUrl() == null || asset.getImageUrl().isEmpty()) {
                asset = generateImage(userId, asset);
            }
        } else {
            asset = createAsset(userId, visualizationKey, stepFields, previousStepFields);
        }
        return asset;
    }

    /**
     * Loads the recipe flow and its ordered step contexts once, up front, so a caller (the async
     * job pipeline) can fan the per-step work out across a {@code TaskPool} without every task
     * re-reading the flow document itself. Each step's {@code previousStepFields} come from the
     * preceding node's already-loaded data — not from any other step's generation result — so the
     * returned steps have no execution-order dependency on each other and can run in parallel.
     *
     * @param recipeId identifier of the recipe flow to prepare
     * @return the loaded flow along with the ordered, pre-computed per-step contexts
     * @throws RecipeFlowGenerationException if the recipe flow cannot be found
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
     *
     * @param flow the recipe flow whose nodes should receive their generated assets
     * @param assetsByStepId map of step (node) id to its successfully generated asset
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

    /**
     * A single recipe step's pre-loaded generation context: its flow node,
     * raw data map, extracted step fields, and the preceding step's fields
     * (for prompt continuity).
     */
    public record StepContext(
            Recipe.NodeDocument node,
            Map<String, Object> data,
            Map<String, Object> stepFields,
            Map<String, Object> previousStepFields
    ) {
    }

    /**
     * The result of {@link #prepareStepContexts}: the loaded recipe flow
     * together with its ordered, ready-to-process step contexts.
     */
    public record RecipeStepPreparation(Recipe flow, List<StepContext> steps) {
    }

    private VisualizationAsset generatePrompt(Long userId, String visualizationKey, Map<String, Object> currentStep, Map<String, Object> previousStep) {
        logger.info("generating prompt");
        try {
            AiRequestOutcome<PromptPair> outcome = queueService.executeInline(
                    userId,
                    AiCapability.TEXT_TO_TEXT,
                    null,
                    null,
                    "visualization-prompt",
                    visualizationKey,
                    selection -> {
                        AIClient client = clientResolver.resolveTextClient(selection.model());
                        AIRequest request = AIRequest.builder()
                                .model(selection.model().getProviderModelId())
                                .systemPrompt(promptBuilder.buildSystemPrompt())
                                .userPrompt(promptBuilder.buildUserPrompt(currentStep, previousStep))
                                .temperature(0.4d)
                                .maxTokens(5000)
                                .build();
                        logger.info("AI Request: {}", objectMapper.writeValueAsString(request));
                        AIResponse response = client.chat(request);
                        return parsePrompts(response == null ? null : response.getContent());
                    }
            );

            PromptPair prompts = outcome.value();
            VisualizationAsset asset = new VisualizationAsset();
            asset.setId(sequenceGeneratorService.generateSequence(VisualizationAsset.SEQUENCE_NAME));
            asset.setVisualizationKey(visualizationKey);
            asset.setType(VisualizationAssetType.ATOMIC);
            asset.setImagePrompt(prompts.imagePrompt());
            asset.setVideoPrompt(prompts.videoPrompt());
            asset.setVideoUrl(null);
            return AIVisualizationAssetRepository.save(asset);
        } catch (Exception e) {
            logger.error("Failed to generate visualization prompt", e);
        }
        return null;
    }

    /**
     * Generates this step's image and places it in object storage, in two separable stages so a
     * storage failure cannot destroy a paid generation.
     * <p>
     * The provider's bytes are persisted as an {@code AiArtifact} <em>before</em> the upload runs
     * (inside {@code executeInline}), and the artifact is only retired once the upload has
     * succeeded. If the upload throws, the artifact stays {@code PENDING} with its payload intact
     * and no expiry, so the next attempt — the next request for this step, or
     * {@code AiArtifactRecoveryJob} — uploads the image that was already paid for instead of
     * generating a new one. The artifact lookup also means a retry after a failure here makes no
     * provider call and spends no credits.
     * <p>
     * Exceptions are still absorbed rather than propagated: {@code VisualizationJobService}
     * distinguishes a failed step by a null {@code imageUrl}, not by a thrown exception. What is
     * new is that the real cause is recorded on the asset instead of being reduced to a log line.
     *
     * @param userId the id of the user this generation's credit charges belong to
     * @param asset the asset whose {@code imagePrompt} should be rendered
     * @return the saved asset, with either an image URL or a failure reason
     */
    private VisualizationAsset generateImage(Long userId, VisualizationAsset asset) {
        String visualizationKey = asset.getVisualizationKey();
        try {
            logger.info("Generating image for visualizationKey: {}, prompt: {}", visualizationKey, asset.getImagePrompt());

            AiArtifactSpec<ImageGenerationClient.GeneratedImage> artifactSpec = AiArtifactSpec.of(
                    AiArtifactKeyBuilder.build(
                            AiCapability.TEXT_TO_IMAGE,
                            "visualization-image",
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
                    "visualization-image",
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
            logger.info("Successfully generated and uploaded image for visualizationKey: {}, prompt: {} (reused={})",
                    visualizationKey, asset.getImagePrompt(), outcome.reused());
        } catch (Exception e) {
            asset.setImageUrl(null);
            asset.setImageFailureReason(describeFailure(e));
            logger.error("Failed to generate or upload image for visualizationKey: {}, prompt: {} "
                            + "(any generated payload is retained for recovery)",
                    visualizationKey, asset.getImagePrompt(), e);
        }
        asset.setVideoUrl(null);
        return AIVisualizationAssetRepository.save(asset);
    }

    private VisualizationAsset createAsset(Long userId, String visualizationKey, Map<String, Object> currentStep, Map<String, Object> previousStep) {
        VisualizationAsset asset = generatePrompt(userId, visualizationKey, currentStep, previousStep);
        requirePrompted(asset, visualizationKey);
        asset = generateImage(userId, asset);
        return AIVisualizationAssetRepository.save(asset);
    }

    /**
     * Supplies the artifact the image consumer works against.
     * <p>
     * Staging is best-effort — {@code AiRequestQueueService} logs and swallows a staging failure
     * rather than failing an otherwise-successful paid call — so the artifact can legitimately be
     * absent. In that case this builds a detached stand-in carrying the same correlation and
     * model metadata, so the upload still happens and simply is not recoverable afterwards. Its
     * null id makes the subsequent {@code markConsumed} a no-op.
     *
     * @param outcome the completed AI request
     * @param visualizationKey the step key, which doubles as the artifact's correlation id
     * @return the persisted artifact, or a detached stand-in if staging did not happen
     */
    private AiArtifact artifactOrTransient(
            AiRequestOutcome<ImageGenerationClient.GeneratedImage> outcome, String visualizationKey) {
        if (outcome.artifact() != null) {
            return outcome.artifact();
        }
        ModelSelectionOutcome selection = outcome.selection();
        AiArtifact transientArtifact = new AiArtifact();
        transientArtifact.setCorrelationId(visualizationKey);
        transientArtifact.setCorrelationType("visualization-image");
        transientArtifact.setCapability(AiCapability.TEXT_TO_IMAGE);
        transientArtifact.setProducedByModelKey(selection.model().getKey());
        transientArtifact.setProducedByTier(selection.model().getTier());
        transientArtifact.setUsedFallback(selection.usedFallback());
        return transientArtifact;
    }

    /**
     * Renders an exception as the short, human-readable reason stored on
     * {@link VisualizationAsset#getImageFailureReason()} and surfaced in the job's step results.
     * Keeps the exception type, since that is what distinguishes a provider failure
     * ({@code AICommunicationException}) from a storage failure and from a malformed response.
     *
     * @param e the failure to describe
     * @return the exception's simple name, with its message appended when it has one
     */
    private String describeFailure(Exception e) {
        String message = e.getMessage();
        return StringUtils.hasText(message)
                ? e.getClass().getSimpleName() + ": " + message
                : e.getClass().getSimpleName();
    }

    /**
     * Guards the one case where {@link #generatePrompt} returns {@code null} — it absorbs every
     * exception and yields null on failure. Callers previously dereferenced that null immediately,
     * so a prompt-model outage surfaced as a bare {@code NullPointerException} in the step result.
     * Failing with a typed exception instead lets the async job record the real reason per step and
     * lets the synchronous endpoint map it through {@code GlobalExceptionHandler}.
     *
     * @param asset the asset returned by {@link #generatePrompt}, possibly null
     * @param visualizationKey the step key being resolved, for the error message
     * @throws RecipeFlowGenerationException if no prompt could be generated for the step
     */
    private void requirePrompted(VisualizationAsset asset, String visualizationKey) {
        if (asset == null) {
            throw new RecipeFlowGenerationException(
                    "Could not generate a visualization prompt for step: " + visualizationKey);
        }
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
