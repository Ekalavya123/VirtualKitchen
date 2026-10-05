package com.processVisualisation.virtualKitchen.ai.workflow;

import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiTaskSelectionDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAiWorkflowException;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Turns "What would you like AI to create?" into a validated, duplicate-free task list.
 * "Complete Recipe Experience" is only a shortcut for every task, so selecting it together with
 * individual tasks (or repeating a task) can never produce a task twice.
 */
public final class RecipeAiTaskSelection {

    private RecipeAiTaskSelection() {
    }

    /**
     * @return the selected tasks, distinct and in {@link RecipeAiTaskType} order
     * @throws RecipeAiWorkflowException (400) if nothing is selected
     */
    public static List<RecipeAiTaskType> normalize(RecipeAiTaskSelectionDTO selection) {
        EnumSet<RecipeAiTaskType> tasks = EnumSet.noneOf(RecipeAiTaskType.class);
        if (selection != null) {
            if (selection.isCompleteExperience()) {
                tasks.addAll(EnumSet.allOf(RecipeAiTaskType.class));
            }
            if (selection.getTasks() != null) {
                selection.getTasks().stream().filter(Objects::nonNull).forEach(tasks::add);
            }
        }
        if (tasks.isEmpty()) {
            throw RecipeAiWorkflowException.badRequest("Select at least one thing for AI to create");
        }
        return List.copyOf(tasks);
    }

    /**
     * Checks a normalised selection against the recipe it is for: generating a process needs recipe
     * text; visuals or narration without a new process need an existing process with steps.
     *
     * @throws RecipeAiWorkflowException (400) if the selection can't run on this recipe
     */
    public static void validate(List<RecipeAiTaskType> tasks, String recipeText, boolean recipeHasSteps) {
        if (tasks.contains(RecipeAiTaskType.PROCESS)) {
            if (!StringUtils.hasText(recipeText)) {
                throw RecipeAiWorkflowException.badRequest("Describe the recipe so AI can create its process");
            }
            return;
        }
        if (!recipeHasSteps) {
            throw RecipeAiWorkflowException.badRequest(
                    "This recipe has no steps yet. Select Recipe Process to create them first.");
        }
    }
}
