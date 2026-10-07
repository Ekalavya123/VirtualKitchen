package com.processVisualisation.virtualKitchen.ai.workflow;

import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiTaskSelectionDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAiWorkflowException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType.NARRATION;
import static com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType.PROCESS;
import static com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType.VISUALS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeAiTaskSelectionTest {

    private static List<RecipeAiTaskType> normalize(boolean complete, RecipeAiTaskType... tasks) {
        return RecipeAiTaskSelection.normalize(new RecipeAiTaskSelectionDTO(List.of(tasks), complete));
    }

    @Test
    void individualSelectionsAreKeptInDependencyOrder() {
        assertEquals(List.of(PROCESS), normalize(false, PROCESS));
        assertEquals(List.of(VISUALS), normalize(false, VISUALS));
        assertEquals(List.of(NARRATION), normalize(false, NARRATION));
        assertEquals(List.of(PROCESS, VISUALS), normalize(false, VISUALS, PROCESS));
        assertEquals(List.of(PROCESS, NARRATION), normalize(false, NARRATION, PROCESS));
        assertEquals(List.of(PROCESS, VISUALS, NARRATION), normalize(false, NARRATION, VISUALS, PROCESS));
    }

    @Test
    void completeRecipeExperienceSelectsEveryTaskOnce() {
        assertEquals(List.of(PROCESS, VISUALS, NARRATION), normalize(true));
        // Ticked together with individual tasks (and repeats): still each task exactly once.
        assertEquals(List.of(PROCESS, VISUALS, NARRATION), normalize(true, VISUALS, VISUALS, PROCESS, NARRATION, PROCESS));
    }

    @Test
    void emptySelectionIsRejected() {
        RecipeAiWorkflowException error = assertThrows(RecipeAiWorkflowException.class, () -> normalize(false));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertThrows(RecipeAiWorkflowException.class, () -> RecipeAiTaskSelection.normalize(null));
    }

    @Test
    void processNeedsRecipeText() {
        assertThrows(RecipeAiWorkflowException.class, () -> RecipeAiTaskSelection.validate(List.of(PROCESS, VISUALS), "  ", false));
        assertDoesNotThrow(() -> RecipeAiTaskSelection.validate(List.of(PROCESS, VISUALS), "Boil 2 eggs", false));
    }

    @Test
    void visualsOrNarrationAloneNeedExistingSteps() {
        assertThrows(RecipeAiWorkflowException.class, () -> RecipeAiTaskSelection.validate(List.of(VISUALS), null, false));
        assertThrows(RecipeAiWorkflowException.class, () -> RecipeAiTaskSelection.validate(List.of(NARRATION), null, false));
        assertDoesNotThrow(() -> RecipeAiTaskSelection.validate(List.of(VISUALS, NARRATION), null, true));
    }
}
