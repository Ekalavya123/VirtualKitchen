package com.processVisualisation.virtualKitchen.recipe.validation;

import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.Process.ProcessEdge;
import com.processVisualisation.virtualKitchen.recipe.model.Process.ProcessNode;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessValidatorTest {

    private static final Long RECIPE_ID = 1L;

    private ProcessRepository processRepository;
    private RecipeTemplateRepository recipeTemplateRepository;
    private ProcessValidator validator;

    @BeforeEach
    void setUp() {
        processRepository = mock(ProcessRepository.class);
        recipeTemplateRepository = mock(RecipeTemplateRepository.class);
        validator = new ProcessValidator(processRepository, recipeTemplateRepository);

        when(recipeTemplateRepository.findById(RECIPE_ID)).thenReturn(Optional.of(new RecipeTemplate()));
        when(processRepository.findByRecipeIdAndType(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void validProcess_withStepAndCondition_passesValidation() {
        Process main = process(1L, ProcessType.MAIN, RECIPE_ID);
        ProcessNode step = stepNode("n1");
        ProcessNode condition = conditionNode("n2");
        main.setNodes(List.of(step, condition));
        main.setEdges(List.of(edge("e1", "n1", "n2")));

        ProcessValidationResult result = validator.validate(main);

        assertTrue(result.isValid(), "expected no errors, got: " + result.getErrors());
    }

    @Test
    void missingRecipeId_isInvalid() {
        Process process = process(1L, ProcessType.MAIN, null);

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("recipeId is required")));
    }

    @Test
    void unknownRecipeId_isInvalid() {
        when(recipeTemplateRepository.findById(999L)).thenReturn(Optional.empty());
        Process process = process(1L, ProcessType.MAIN, 999L);

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unknown recipe")));
    }

    @Test
    void duplicateNodeIds_isInvalid() {
        Process process = process(1L, ProcessType.MAIN, RECIPE_ID);
        process.setNodes(List.of(stepNode("n1"), stepNode("n1")));

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("duplicate node id")));
    }

    @Test
    void edgeReferencingUnknownNode_isInvalid() {
        Process process = process(1L, ProcessType.MAIN, RECIPE_ID);
        process.setNodes(List.of(stepNode("n1")));
        process.setEdges(List.of(edge("e1", "n1", "missing")));

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("target references unknown node")));
    }

    @Test
    void stepNodeWithoutData_isInvalid() {
        Process process = process(1L, ProcessType.MAIN, RECIPE_ID);
        ProcessNode step = new ProcessNode();
        step.setId("n1");
        step.setKind(ProcessNodeKind.STEP);
        process.setNodes(List.of(step));

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("is a STEP but has no step data")));
    }

    @Test
    void conditionNodeWithoutData_isInvalid() {
        Process process = process(1L, ProcessType.MAIN, RECIPE_ID);
        ProcessNode condition = new ProcessNode();
        condition.setId("n1");
        condition.setKind(ProcessNodeKind.CONDITION);
        process.setNodes(List.of(condition));

        ProcessValidationResult result = validator.validate(process);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("is a CONDITION but has no condition data")));
    }

    @Test
    void secondMainProcessForSameRecipe_isInvalid() {
        Process existingMain = process(5L, ProcessType.MAIN, RECIPE_ID);
        when(processRepository.findByRecipeIdAndType(RECIPE_ID, ProcessType.MAIN)).thenReturn(List.of(existingMain));

        Process newMain = process(6L, ProcessType.MAIN, RECIPE_ID);
        newMain.setNodes(List.of(stepNode("n1")));

        ProcessValidationResult result = validator.validate(newMain);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("already has a MAIN process")));
    }

    @Test
    void updatingTheExistingMainProcess_isNotFlaggedAsADuplicate() {
        Process existingMain = process(5L, ProcessType.MAIN, RECIPE_ID);
        when(processRepository.findByRecipeIdAndType(RECIPE_ID, ProcessType.MAIN)).thenReturn(List.of(existingMain));

        existingMain.setNodes(List.of(stepNode("n1")));

        ProcessValidationResult result = validator.validate(existingMain);

        assertTrue(result.isValid(), "re-validating the same MAIN process must not conflict with itself: " + result.getErrors());
    }

    @Test
    void validateOrThrow_throwsWithAllErrorsJoined() {
        Process process = process(1L, ProcessType.MAIN, null);

        ProcessValidationException ex = assertThrows(ProcessValidationException.class, () -> validator.validateOrThrow(process));

        assertTrue(ex.getMessage().contains("recipeId is required"));
    }

    @Test
    void validateOrThrow_validProcess_doesNotThrow() {
        Process process = process(1L, ProcessType.MAIN, RECIPE_ID);
        process.setNodes(List.of(stepNode("n1")));

        validator.validateOrThrow(process);
    }

    // --- test fixtures ---

    private static Process process(Long id, ProcessType type, Long recipeId) {
        Process process = new Process();
        process.setId(id);
        process.setType(type);
        process.setRecipeId(recipeId);
        process.setName("Test process " + id);
        return process;
    }

    private static ProcessNode stepNode(String id) {
        ProcessNode node = new ProcessNode();
        node.setId(id);
        node.setKind(ProcessNodeKind.STEP);
        node.setData(Map.of("action", "chop"));
        return node;
    }

    private static ProcessNode conditionNode(String id) {
        ProcessNode node = new ProcessNode();
        node.setId(id);
        node.setKind(ProcessNodeKind.CONDITION);
        node.setData(Map.of("title", "Is it cooked?"));
        return node;
    }

    private static ProcessEdge edge(String id, String source, String target) {
        ProcessEdge edge = new ProcessEdge();
        edge.setId(id);
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }
}
