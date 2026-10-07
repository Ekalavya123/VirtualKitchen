package com.processVisualisation.virtualKitchen.ai.workflow.dto;

import com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * What the user ticked in "What would you like AI to create?". {@code completeExperience} is the
 * convenience option and selects every task; the explicit {@code tasks} list may repeat entries or
 * overlap with it — the selection is normalised to a distinct set either way.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAiTaskSelectionDTO {

    private List<RecipeAiTaskType> tasks = new ArrayList<>();

    private boolean completeExperience;
}
