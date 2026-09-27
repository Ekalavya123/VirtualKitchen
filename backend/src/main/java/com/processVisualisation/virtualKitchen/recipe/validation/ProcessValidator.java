package com.processVisualisation.virtualKitchen.recipe.validation;

import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.Process.ProcessEdge;
import com.processVisualisation.virtualKitchen.recipe.model.Process.ProcessNode;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a {@link Process} before it is persisted. A process is always a
 * single, self-contained graph of STEP/CONDITION nodes — it never embeds
 * another process as a node — so this only checks structural integrity of
 * its own node/edge graph (unique/known ids, per-kind required data) plus
 * two cross-document rules: {@code recipeId} must resolve to an existing
 * recipe, and a recipe may have at most one MAIN process. A STEP's Action On
 * references to ingredients/subprocesses live inside its opaque {@code data}
 * field bag and are a frontend concern, not validated here (the same way an
 * ingredientId was never cross-checked against the ingredient catalog).
 * <p>
 * The one exception is Action On <em>step-output</em> references
 * ({@code data.step.actionOn.steps[].stepId}), because they point at other nodes of this same
 * graph: each must name an existing STEP (not a CONDITION, not itself) that has an Expected Output
 * and comes before the consuming step — an edge path leads from it to the consumer, with node
 * order breaking the tie when both reach each other through a loop. This mirrors the frontend's
 * recipe-tool/process/model/recipeStepOutputs.ts, so a reference the editor offers always saves.
 */
@Component
public class ProcessValidator {

    private final ProcessRepository processRepository;
    private final RecipeTemplateRepository recipeTemplateRepository;

    public ProcessValidator(ProcessRepository processRepository, RecipeTemplateRepository recipeTemplateRepository) {
        this.processRepository = processRepository;
        this.recipeTemplateRepository = recipeTemplateRepository;
    }

    /**
     * Validates a process, collecting every structural error found rather
     * than failing fast on the first one.
     *
     * @param process the process to validate
     * @return a result indicating whether the process is valid and, if not, the list of errors
     */
    public ProcessValidationResult validate(Process process) {
        List<String> errors = new ArrayList<>();

        if (process == null) {
            errors.add("process is required");
            return new ProcessValidationResult(false, errors);
        }

        validateRecipeOwnership(process, errors);
        Set<String> nodeIds = validateNodes(process, errors);
        validateEdges(process, nodeIds, errors);
        validateStepOutputReferences(process, errors);
        validateSingleMainProcess(process, errors);

        return new ProcessValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Convenience wrapper around {@link #validate(Process)} that throws
     * instead of returning a result, for callers that just want to reject
     * an invalid process outright.
     *
     * @param process the process to validate
     * @throws ProcessValidationException if the process is invalid, with every error joined into the message
     */
    public void validateOrThrow(Process process) {
        ProcessValidationResult result = validate(process);
        if (!result.isValid()) {
            throw new ProcessValidationException(String.join("; ", result.getErrors()));
        }
    }

    private void validateRecipeOwnership(Process process, List<String> errors) {
        if (process.getRecipeId() == null) {
            errors.add("process.recipeId is required");
            return;
        }

        if (recipeTemplateRepository.findById(process.getRecipeId()).isEmpty()) {
            errors.add("process.recipeId references an unknown recipe: " + process.getRecipeId());
        }
    }

    private Set<String> validateNodes(Process process, List<String> errors) {
        Set<String> nodeIds = new HashSet<>();
        List<ProcessNode> nodes = process.getNodes() == null ? List.of() : process.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            ProcessNode node = nodes.get(i);
            if (node == null) {
                errors.add("node[" + i + "] is null");
                continue;
            }

            if (isBlank(node.getId())) {
                errors.add("node[" + i + "].id is required");
            } else if (!nodeIds.add(node.getId())) {
                errors.add("duplicate node id: " + node.getId());
            }

            validateNodeKind(node, i, errors);
        }

        return nodeIds;
    }

    private void validateNodeKind(ProcessNode node, int index, List<String> errors) {
        ProcessNodeKind kind = node.getKind();
        if (kind == null) {
            errors.add("node[" + index + "].kind is required");
            return;
        }

        switch (kind) {
            case STEP -> {
                if (isEmptyData(node.getData())) {
                    errors.add("node[" + index + "] (" + node.getId() + ") is a STEP but has no step data");
                }
            }
            case CONDITION -> {
                if (isEmptyData(node.getData())) {
                    errors.add("node[" + index + "] (" + node.getId() + ") is a CONDITION but has no condition data");
                }
            }
        }
    }

    private void validateEdges(Process process, Set<String> nodeIds, List<String> errors) {
        List<ProcessEdge> edges = process.getEdges() == null ? List.of() : process.getEdges();
        Set<String> edgeIds = new HashSet<>();

        for (int i = 0; i < edges.size(); i++) {
            ProcessEdge edge = edges.get(i);
            if (edge == null) {
                errors.add("edge[" + i + "] is null");
                continue;
            }

            if (!isBlank(edge.getId()) && !edgeIds.add(edge.getId())) {
                errors.add("duplicate edge id: " + edge.getId());
            }

            if (isBlank(edge.getSource())) {
                errors.add("edge[" + i + "].source is required");
            } else if (!nodeIds.contains(edge.getSource())) {
                errors.add("edge[" + i + "].source references unknown node: " + edge.getSource());
            }

            if (isBlank(edge.getTarget())) {
                errors.add("edge[" + i + "].target is required");
            } else if (!nodeIds.contains(edge.getTarget())) {
                errors.add("edge[" + i + "].target references unknown node: " + edge.getTarget());
            }
        }
    }

    private void validateStepOutputReferences(Process process, List<String> errors) {
        List<ProcessNode> nodes = process.getNodes() == null ? List.of() : process.getNodes();
        Map<String, ProcessNode> nodeById = new LinkedHashMap<>();
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            ProcessNode node = nodes.get(i);
            if (node == null || isBlank(node.getId())) continue;
            nodeById.putIfAbsent(node.getId(), node);
            nodeIndex.putIfAbsent(node.getId(), i);
        }

        Map<String, List<String>> successors = new HashMap<>();
        for (ProcessEdge edge : process.getEdges() == null ? List.<ProcessEdge>of() : process.getEdges()) {
            if (edge == null || isBlank(edge.getSource()) || isBlank(edge.getTarget())) continue;
            successors.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
        }

        for (ProcessNode consumer : nodeById.values()) {
            if (consumer.getKind() != ProcessNodeKind.STEP) continue;
            for (String sourceId : stepOutputReferences(consumer)) {
                String label = "node " + consumer.getId() + " step-output reference " + sourceId;
                ProcessNode source = nodeById.get(sourceId);
                if (sourceId.equals(consumer.getId())) {
                    errors.add(label + ": a step cannot use its own output");
                } else if (source == null) {
                    errors.add(label + ": references an unknown node");
                } else if (source.getKind() != ProcessNodeKind.STEP) {
                    errors.add(label + ": only a STEP's output can be referenced, not a " + source.getKind());
                } else if (isBlank(expectedOutput(source))) {
                    errors.add(label + ": the referenced step has no Expected Output");
                } else if (!comesBefore(sourceId, consumer.getId(), successors, nodeIndex)) {
                    errors.add(label + ": the referenced step is not connected before the consuming step");
                }
            }
        }
    }

    /** Earlier-than: {@code sourceId} reaches {@code consumerId} along edges, and in a loop (each reaches the other) it is also earlier in node order. */
    private boolean comesBefore(String sourceId, String consumerId, Map<String, List<String>> successors, Map<String, Integer> nodeIndex) {
        if (!reaches(sourceId, consumerId, successors)) return false;
        return !reaches(consumerId, sourceId, successors) || nodeIndex.get(sourceId) < nodeIndex.get(consumerId);
    }

    private boolean reaches(String from, String to, Map<String, List<String>> successors) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(successors.getOrDefault(from, List.of()));
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) return true;
            if (seen.add(current)) queue.addAll(successors.getOrDefault(current, List.of()));
        }
        return false;
    }

    /** {@code data.step.actionOn.steps[].stepId} — the shape recipe-tool/process/model/recipeStepData.ts writes. */
    private List<String> stepOutputReferences(ProcessNode node) {
        List<String> ids = new ArrayList<>();
        if (!(stepData(node).get("actionOn") instanceof Map<?, ?> actionOn)) return ids;
        if (!(actionOn.get("steps") instanceof List<?> references)) return ids;
        for (Object reference : references) {
            Object stepId = reference instanceof Map<?, ?> entry ? entry.get("stepId") : null;
            ids.add(stepId == null ? "" : String.valueOf(stepId));
        }
        return ids;
    }

    private String expectedOutput(ProcessNode node) {
        Object value = stepData(node).get("expectedOutput");
        return value == null ? "" : String.valueOf(value);
    }

    private Map<?, ?> stepData(ProcessNode node) {
        return node.getData() != null && node.getData().get("step") instanceof Map<?, ?> step ? step : Map.of();
    }

    private void validateSingleMainProcess(Process process, List<String> errors) {
        if (process.getType() != ProcessType.MAIN || process.getRecipeId() == null) {
            return;
        }

        boolean anotherMainExists = processRepository.findByRecipeIdAndType(process.getRecipeId(), ProcessType.MAIN)
                .stream()
                .anyMatch(existing -> process.getId() == null || !process.getId().equals(existing.getId()));

        if (anotherMainExists) {
            errors.add("recipe " + process.getRecipeId() + " already has a MAIN process");
        }
    }

    private boolean isEmptyData(Map<String, Object> data) {
        return data == null || data.isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
