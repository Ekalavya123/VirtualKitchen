package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionEdgeDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionStepDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates AI-generated (or user-supplied) recipe execution flows before
 * they are persisted or visualized. Checks structural integrity of the
 * step/edge graph: required fields per node type, unique ids, edge
 * references, condition-node YES/NO branching, and matching parallel
 * start/end pairs.
 */
@Component
public class AIRecipeValidator {

    private static final String NODE_TYPE_RECIPE_STEP = "recipeStep";
    private static final String NODE_TYPE_CONDITION = "condition";
    private static final String NODE_TYPE_PARALLEL_START = "parallelStart";
    private static final String NODE_TYPE_PARALLEL_END = "parallelEnd";

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            NODE_TYPE_RECIPE_STEP,
            NODE_TYPE_CONDITION,
            NODE_TYPE_PARALLEL_START,
            NODE_TYPE_PARALLEL_END
    );

    /**
     * Validates a recipe flow's steps and edges, collecting all structural
     * errors found rather than failing fast on the first one.
     *
     * @param steps the flow's step/node definitions
     * @param edges the flow's edges connecting steps
     * @return a result indicating whether the flow is valid and, if not, the
     *         list of validation error messages
     */
    public AIRecipeFlowValidationResult validate(List<RecipeExecutionStepDTO> steps, List<RecipeExecutionEdgeDTO> edges) {
        List<String> errors = new ArrayList<>();

        if (steps == null) {
            errors.add("steps must be an array");
            return new AIRecipeFlowValidationResult(false, errors);
        }
        if (edges == null) {
            errors.add("edges must be an array");
            return new AIRecipeFlowValidationResult(false, errors);
        }

        Set<String> nodeIds = new LinkedHashSet<>();
        Set<String> edgeKeys = new HashSet<>();
        Map<String, RecipeExecutionStepDTO> stepById = new HashMap<>();
        Map<String, Integer> outgoingByNode = new HashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            RecipeExecutionStepDTO step = steps.get(i);
            validateStep(step, i, nodeIds, stepById, errors);
        }

        for (int i = 0; i < edges.size(); i++) {
            RecipeExecutionEdgeDTO edge = edges.get(i);
            validateEdge(edge, i, edgeKeys, nodeIds, stepById, outgoingByNode, errors);
        }

        validateConditionBranching(stepById, outgoingByNode, errors);
        validateParallelPairs(stepById, errors);

        return new AIRecipeFlowValidationResult(errors.isEmpty(), errors);
    }

    private void validateStep(
            RecipeExecutionStepDTO step,
            int index,
            Set<String> stepIds,
            Map<String, RecipeExecutionStepDTO> stepById,
            List<String> errors
    ) {
        if (step == null) {
            errors.add("step[" + index + "] is null");
            return;
        }

        String id = asString(step.getId());
        String nodeType = asString(step.getNodeType());
        Map<String, Object> data = step.getData();

        if (isBlank(id)) {
            errors.add("step[" + index + "].id is required");
        } else if (!stepIds.add(id)) {
            errors.add("step id must be unique: " + id);
        } else {
            stepById.put(id, step);
        }

        if (isBlank(nodeType)) {
            errors.add("step[" + index + "].nodeType is required");
            return;
        }

        if (!SUPPORTED_NODE_TYPES.contains(nodeType)) {
            errors.add("step[" + index + "].nodeType is invalid: " + nodeType);
            return;
        }

        if (data == null) {
            errors.add("step[" + index + "].data is required");
            return;
        }

        if (NODE_TYPE_RECIPE_STEP.equals(nodeType)) {
            validateRecipeStepData(data, index, errors);
            return;
        }

        if (NODE_TYPE_CONDITION.equals(nodeType)) {
            validateConditionData(data, index, errors);
            return;
        }

        validateParallelData(data, index, nodeType, errors);
    }

    private void validateRecipeStepData(Map<String, Object> data, int index, List<String> errors) {
        String action = asString(data.get("action"));
        if (isBlank(action)) {
            errors.add("step[" + index + "].data.action is required for recipeStep");
        }
    }

    private void validateConditionData(Map<String, Object> data, int index, List<String> errors) {
        String title = asString(data.get("title"));
        String expectedResult = asString(data.get("expectedResult"));

        if (isBlank(title)) {
            errors.add("step[" + index + "].data.title is required for condition");
        }

        if (!isBlank(expectedResult)
                && !("success".equalsIgnoreCase(expectedResult) || "failure".equalsIgnoreCase(expectedResult))) {
            errors.add("step[" + index + "].data.expectedResult must be success or failure");
        }
    }

    private void validateParallelData(Map<String, Object> data, int index, String nodeType, List<String> errors) {
        String title = asString(data.get("title"));
        if (isBlank(title)) {
            errors.add("step[" + index + "].data.title is required for " + nodeType);
        }
    }

    private void validateEdge(
            RecipeExecutionEdgeDTO edge,
            int index,
            Set<String> edgeKeys,
            Set<String> stepIds,
            Map<String, RecipeExecutionStepDTO> stepById,
            Map<String, Integer> outgoingByNode,
            List<String> errors
    ) {
        if (edge == null) {
            errors.add("edge[" + index + "] is null");
            return;
        }

        String from = asString(edge.getFrom());
        String to = asString(edge.getTo());
        String label = asString(edge.getLabel());

        if (isBlank(from)) {
            errors.add("edge[" + index + "].from is required");
        } else if (!stepIds.contains(from)) {
            errors.add("edge[" + index + "].from references unknown step: " + from);
        }

        if (isBlank(to)) {
            errors.add("edge[" + index + "].to is required");
        } else if (!stepIds.contains(to)) {
            errors.add("edge[" + index + "].to references unknown step: " + to);
        }

        if (!isBlank(from) && !isBlank(to)) {
            String key = from + "->" + to + "::" + label;
            if (!edgeKeys.add(key)) {
                errors.add("duplicate edge from " + from + " to " + to + " with label " + label);
            }

            int currentCount = outgoingByNode.getOrDefault(from, 0);
            outgoingByNode.put(from, currentCount + 1);
            validateConditionEdgeLabel(index, from, label, stepById, errors);
        }
    }

    private void validateConditionEdgeLabel(
            int edgeIndex,
            String from,
            String label,
            Map<String, RecipeExecutionStepDTO> stepById,
            List<String> errors
    ) {
        RecipeExecutionStepDTO source = stepById.get(from);
        if (source == null || !NODE_TYPE_CONDITION.equalsIgnoreCase(asString(source.getNodeType()))) {
            return;
        }

        if (isBlank(label)) {
            errors.add("edge[" + edgeIndex + "] from condition node " + from + " must have label YES or NO");
            return;
        }

        String upper = label.trim().toUpperCase();
        if (!"YES".equals(upper) && !"NO".equals(upper)) {
            errors.add("edge[" + edgeIndex + "] from condition node " + from + " has invalid label: " + label);
        }
    }

    private void validateConditionBranching(
            Map<String, RecipeExecutionStepDTO> stepById,
            Map<String, Integer> outgoingByNode,
            List<String> errors
    ) {
        for (Map.Entry<String, RecipeExecutionStepDTO> entry : stepById.entrySet()) {
            String nodeId = entry.getKey();
            RecipeExecutionStepDTO step = entry.getValue();
            if (!NODE_TYPE_CONDITION.equalsIgnoreCase(asString(step.getNodeType()))) {
                continue;
            }

            int outgoingCount = outgoingByNode.getOrDefault(nodeId, 0);
            if (outgoingCount < 2) {
                errors.add("condition node " + nodeId + " should have YES and NO outgoing edges");
            }
        }
    }

    private void validateParallelPairs(Map<String, RecipeExecutionStepDTO> stepById, List<String> errors) {
        int startCount = 0;
        int endCount = 0;

        for (RecipeExecutionStepDTO step : stepById.values()) {
            String nodeType = asString(step.getNodeType());
            if (NODE_TYPE_PARALLEL_START.equals(nodeType)) {
                startCount++;
            }
            if (NODE_TYPE_PARALLEL_END.equals(nodeType)) {
                endCount++;
            }
        }

        if (startCount > endCount) {
            errors.add("every parallelStart must eventually connect to a parallelEnd");
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
