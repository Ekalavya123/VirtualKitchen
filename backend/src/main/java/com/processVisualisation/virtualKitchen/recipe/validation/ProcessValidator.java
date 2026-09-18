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

import java.util.ArrayList;
import java.util.HashSet;
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
