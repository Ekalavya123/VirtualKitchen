package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.dto.NutritionInfoDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeDetailResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientDTO;
import com.processVisualisation.virtualKitchen.recipe.model.NutritionInfo;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredient;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-level tests for the new Recipe Tool methods added to
 * {@link RecipeTemplateServiceImpl} (recipe details, ingredients, nutrition,
 * main process). Pre-existing methods (create/update/delete/copyToUser) are
 * not re-tested here — they were untested before this change and are
 * untouched by it. {@link IProcessService} is mocked so these tests exercise
 * only the recipe-level orchestration/ownership/visibility logic, not
 * {@link ProcessServiceImpl} itself (covered separately by
 * {@code ProcessServiceImplTest}). Uses {@link ReflectionTestUtils} to
 * inject the class's existing field-injected dependencies, since this
 * pre-existing class is not being switched to constructor injection here.
 */
class RecipeTemplateServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private final Map<Long, RecipeTemplate> recipeStore = new HashMap<>();

    private IProcessService processService;
    private RecipeTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        recipeStore.clear();

        RecipeTemplateRepository repo = mock(RecipeTemplateRepository.class);
        when(repo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(recipeStore.get(inv.<Long>getArgument(0))));
        when(repo.save(any())).thenAnswer(inv -> {
            RecipeTemplate recipe = inv.getArgument(0);
            recipeStore.put(recipe.getId(), recipe);
            return recipe;
        });

        processService = mock(IProcessService.class);

        service = new RecipeTemplateServiceImpl();
        ReflectionTestUtils.setField(service, "repo", repo);
        ReflectionTestUtils.setField(service, "mapper", new ProcessTemplateMapper());
        ReflectionTestUtils.setField(service, "seq", mock(SequenceGeneratorService.class));
        ReflectionTestUtils.setField(service, "recipeRepository", mock(RecipeRepository.class));
        ReflectionTestUtils.setField(service, "processService", processService);
    }

    @Test
    void getRecipeDetails_includesIngredientsNutritionAndMainProcessId() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        RecipeTemplate recipe = recipeStore.get(recipeId);

        RecipeIngredient ingredient = new RecipeIngredient();
        ingredient.setIngredientId(5L);
        ingredient.setQuantity(2.0);
        ingredient.setUnit(UnitType.COUNT);
        recipe.setIngredients(List.of(ingredient));

        NutritionInfo nutrition = new NutritionInfo();
        nutrition.setCalories(250.0);
        recipe.setNutrition(nutrition);
        recipe.setMainProcessId(555L);

        RecipeDetailResponseDTO details = service.getRecipeDetails(recipeId, OWNER_ID);

        assertEquals(1, details.getIngredients().size());
        assertEquals(5L, details.getIngredients().get(0).getIngredientId());
        assertEquals(250.0, details.getNutrition().getCalories());
        assertEquals(555L, details.getMainProcessId());
    }

    @Test
    void getRecipeDetails_privateRecipe_nonOwner_isForbidden() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);

        assertThrows(RecipeAccessDeniedException.class, () -> service.getRecipeDetails(recipeId, OTHER_USER_ID));
    }

    @Test
    void getRecipeDetails_publicRecipe_nonOwnerCanRead() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PUBLIC);

        RecipeDetailResponseDTO details = service.getRecipeDetails(recipeId, OTHER_USER_ID);

        assertEquals(recipeId, details.getId());
    }

    @Test
    void getRecipeDetails_unknownRecipe_notFound() {
        assertThrows(NoSuchElementException.class, () -> service.getRecipeDetails(999L, OWNER_ID));
    }

    @Test
    void updateIngredients_owner_replacesList() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        RecipeIngredientDTO dto = new RecipeIngredientDTO();
        dto.setIngredientId(9L);
        dto.setQuantity(3.0);
        dto.setUnit(UnitType.GRAM);

        RecipeDetailResponseDTO updated = service.updateIngredients(recipeId, OWNER_ID, List.of(dto));

        assertEquals(1, updated.getIngredients().size());
        assertEquals(9L, updated.getIngredients().get(0).getIngredientId());
    }

    @Test
    void updateIngredients_nonOwner_isRejected() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);

        assertThrows(RecipeAccessDeniedException.class, () -> service.updateIngredients(recipeId, OTHER_USER_ID, List.of()));
    }

    @Test
    void updateNutrition_owner_replacesNutrition() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        NutritionInfoDTO dto = new NutritionInfoDTO();
        dto.setCalories(400.0);
        dto.setServings(4);

        RecipeDetailResponseDTO updated = service.updateNutrition(recipeId, OWNER_ID, dto);

        assertEquals(400.0, updated.getNutrition().getCalories());
        assertEquals(4, updated.getNutrition().getServings());
    }

    @Test
    void updateNutrition_nonOwner_isRejected() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);

        assertThrows(RecipeAccessDeniedException.class, () -> service.updateNutrition(recipeId, OTHER_USER_ID, new NutritionInfoDTO()));
    }

    @Test
    void createMainProcess_noExistingMain_createsAndAssigns() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        ProcessResponseDTO created = ProcessResponseDTO.builder()
                .id(777L).type(ProcessType.MAIN).recipeId(recipeId).name("Recipe " + recipeId).build();
        when(processService.create(eq(recipeId), eq(OWNER_ID), any())).thenReturn(created);

        ProcessResponseDTO result = service.createMainProcess(recipeId, OWNER_ID);

        assertEquals(777L, result.getId());
        assertEquals(777L, recipeStore.get(recipeId).getMainProcessId());
        verify(processService).create(eq(recipeId), eq(OWNER_ID), any());
    }

    @Test
    void createMainProcess_alreadyExists_isIdempotentAndDoesNotCreateAgain() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        recipeStore.get(recipeId).setMainProcessId(555L);
        ProcessResponseDTO existing = ProcessResponseDTO.builder().id(555L).type(ProcessType.MAIN).recipeId(recipeId).build();
        when(processService.get(recipeId, 555L, OWNER_ID)).thenReturn(existing);

        ProcessResponseDTO result = service.createMainProcess(recipeId, OWNER_ID);

        assertEquals(555L, result.getId());
        verify(processService, never()).create(any(), any(), any());
    }

    @Test
    void createMainProcess_nonOwner_isRejected() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);

        assertThrows(RecipeAccessDeniedException.class, () -> service.createMainProcess(recipeId, OTHER_USER_ID));
    }

    @Test
    void getMainProcess_delegatesToProcessService() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);
        recipeStore.get(recipeId).setMainProcessId(555L);
        ProcessResponseDTO existing = ProcessResponseDTO.builder().id(555L).build();
        when(processService.get(recipeId, 555L, OWNER_ID)).thenReturn(existing);

        ProcessResponseDTO result = service.getMainProcess(recipeId, OWNER_ID);

        assertEquals(555L, result.getId());
    }

    @Test
    void getMainProcess_noMainProcessYet_throwsNotFound() {
        Long recipeId = seedRecipe(OWNER_ID, Visibility.PRIVATE);

        assertThrows(NoSuchElementException.class, () -> service.getMainProcess(recipeId, OWNER_ID));
    }

    private Long seedRecipe(Long ownerId, Visibility visibility) {
        long id = 100 + recipeStore.size();
        RecipeTemplate recipe = new RecipeTemplate();
        recipe.setId(id);
        recipe.setName("Recipe " + id);
        recipe.setCreatedBy(ownerId);
        recipe.setVisibility(visibility);
        recipeStore.put(id, recipe);
        return id;
    }
}
