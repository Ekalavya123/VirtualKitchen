package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnIngredientDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedRecipeProcessDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedRecipeStepDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Maps the compact model output ({@link RecipeProcessOutput}) onto the {@code Generated*DTO}s the validator and the
 * frontend work with, filling every default the model was told to omit: nodeType STEP, expectedOutput "",
 * expectedResult success, empty target lists, and a stepId for steps that no later step references.
 * Values are carried over as-is — deciding whether they are valid is {@link RecipeProcessGenerationValidator}'s job.
 */
@Component
public class RecipeProcessOutputNormalizer {

    public GeneratedRecipeProcessDTO toProcess(RecipeProcessOutput.Process process) {
        if (process == null) return null;
        List<GeneratedRecipeStepDTO> steps = null;
        if (process.steps() != null) {
            Set<String> takenStepIds = new HashSet<>();
            process.steps().stream().filter(Objects::nonNull).map(RecipeProcessOutput.Node::stepId)
                    .filter(Objects::nonNull).forEach(takenStepIds::add);
            steps = new ArrayList<>();
            for (int i = 0; i < process.steps().size(); i++) {
                steps.add(toStep(process.steps().get(i), i, takenStepIds));
            }
        }
        return new GeneratedRecipeProcessDTO(process.ref(), process.name(), steps);
    }

    public List<GeneratedRecipeProcessDTO> toSubprocesses(List<RecipeProcessOutput.Process> subprocesses) {
        if (subprocesses == null) return List.of();
        List<GeneratedRecipeProcessDTO> result = new ArrayList<>();
        subprocesses.forEach(subprocess -> result.add(toProcess(subprocess)));
        return result;
    }

    private GeneratedRecipeStepDTO toStep(RecipeProcessOutput.Node node, int index, Set<String> takenStepIds) {
        if (node == null) return null;
        GeneratedRecipeStepDTO step = new GeneratedRecipeStepDTO();
        step.setNodeType(node.nodeType() == null || node.nodeType().isBlank() ? "STEP" : node.nodeType());
        step.setActionDescription(node.actionDescription());
        step.setExpectedOutput(node.expectedOutput() == null ? "" : node.expectedOutput());

        if ("CONDITION".equals(step.getNodeType())) {
            step.setTitle(node.title());
            step.setExpectedResult(node.expectedResult() == null || node.expectedResult().isBlank() ? "success" : node.expectedResult());
            return step;
        }

        step.setStepId(node.stepId() != null ? node.stepId() : generateStepId(index, takenStepIds));
        step.setAction(node.action());
        step.setCustomActionName(node.customActionName());
        step.setActionOn(new GeneratedActionOnDTO(
                node.ingredients() == null ? List.of() : node.ingredients().stream().map(this::toIngredient).toList(),
                node.processes() == null ? List.of() : node.processes(),
                node.fromSteps() == null ? List.of() : node.fromSteps()
        ));
        step.setTemperatureValue(node.temperatureValue());
        step.setTemperatureUnit(node.temperatureUnit());
        step.setFlameLevel(node.flameLevel());
        step.setDuration(node.duration());
        step.setRepeatInterval(node.repeatInterval());
        return step;
    }

    private GeneratedActionOnIngredientDTO toIngredient(RecipeProcessOutput.Ingredient ingredient) {
        if (ingredient == null) return null;
        return new GeneratedActionOnIngredientDTO(
                ingredient.ingredientId(),
                ingredient.quantity(),
                ingredient.unit(),
                ingredient.preparationStyle(),
                ingredient.customIngredientName()
        );
    }

    /** "s<position>", suffixed until it collides with no stepId the model wrote itself. */
    private static String generateStepId(int index, Set<String> takenStepIds) {
        String candidate = "s" + (index + 1);
        while (!takenStepIds.add(candidate)) {
            candidate = candidate + "_";
        }
        return candidate;
    }
}
