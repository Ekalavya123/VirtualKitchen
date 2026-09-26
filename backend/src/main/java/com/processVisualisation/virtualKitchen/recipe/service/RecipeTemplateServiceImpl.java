package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.dto.NutritionInfoDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeDetailResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientDTO;
import com.processVisualisation.virtualKitchen.recipe.model.NutritionInfo;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredient;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of IProcessTemplateService. Persists recipe templates via
 * RecipeTemplateRepository, enforces ownership (via requireOwner) on mutating operations,
 * and, when a public template is copied to a user library via copyToUser, deep-clones the
 * template's ingredients, nutrition and Process documents onto the new template so the copy is fully independent of the original. Maps between entities and
 * DTOs via ProcessTemplateMapper.
 */
@Service
public class RecipeTemplateServiceImpl implements IProcessTemplateService {

    @Autowired
    private RecipeTemplateRepository repo;

    @Autowired
    private ProcessTemplateMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Autowired
    private IProcessService processService;

    /**
     * Creates and persists a new recipe template, assigning it a new sequence-generated id.
     *
     * @param dto the template details to persist
     * @return the created template
     */
    @Override
    public RecipeTemplateResponseDTO create(RecipeTemplateRequestDTO dto){
        RecipeTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(RecipeTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Retrieves a single recipe template by id.
     *
     * @param id the id of the template to fetch
     * @return the matching template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     */
    @Override
    public RecipeTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    /**
     * Retrieves all recipe templates owned by a given user.
     *
     * @param userId the id of the owning user to filter by
     * @return the templates created by that user
     */
    @Override
    public List<RecipeTemplateResponseDTO> getByUser(Long userId){
        return repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all publicly visible recipe templates that were not created by the given user.
     *
     * @param userId the id of the user to exclude as owner
     * @return the matching public templates owned by other users
     */
    @Override
    public List<RecipeTemplateResponseDTO> getGlobalRecipes(Long userId){
        return repo.findByVisibilityAndCreatedByNot(Visibility.PUBLIC, userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates the name and description of a recipe template, after verifying the requesting
     * user owns it.
     *
     * @param id     the id of the template to update
     * @param userId the id of the user requesting the update, used for ownership verification
     * @param dto    the new name/description values
     * @return the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public RecipeTemplateResponseDTO update(Long id, Long userId, RecipeTemplateUpdateDTO dto){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Deletes a recipe template, after verifying the requesting user owns it.
     *
     * @param id     the id of the template to delete
     * @param userId the id of the user requesting the deletion, used for ownership verification
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public void delete(Long id, Long userId){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        repo.deleteById(id);
    }

    /**
     * Updates the visibility (e.g. private/public) of a recipe template, after verifying the
     * requesting user owns it.
     *
     * @param id         the id of the template to update
     * @param userId     the id of the user requesting the update, used for ownership verification
     * @param visibility the new visibility value
     * @return the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public RecipeTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setVisibility(visibility);
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Copies a publicly visible recipe template into a new private template owned by the given
     * user, as a complete, independent copy: its ingredients and nutrition, every Process
     * document (MAIN and every SUBPROCESS, with process ids and Action On subprocess references
     * remapped to the copies — see {@link IProcessService#copyAllToRecipe}) with the copy's
     * mainProcessId pointing at the copied MAIN.
     *
     * @param id     the id of the public template to copy
     * @param userId the id of the user the copy will be owned by
     * @return the newly created copy
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if the source template is not Visibility.PUBLIC
     */
    @Override
    public RecipeTemplateResponseDTO copyToUser(Long id, Long userId){
        RecipeTemplate original = repo.findById(id).orElseThrow();

        if (original.getVisibility() != Visibility.PUBLIC) {
            throw new RecipeAccessDeniedException("Only public recipes can be added to My Recipes");
        }

        RecipeTemplate copy = new RecipeTemplate();
        copy.setId(seq.generateSequence(RecipeTemplate.SEQUENCE_NAME));
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setCreatedBy(userId);
        copy.setVisibility(Visibility.PRIVATE);
        copy.setIngredients(cloneIngredients(original.getIngredients()));
        copy.setNutrition(cloneNutrition(original.getNutrition()));

        // Saved before its processes are copied: ProcessValidator requires each process's recipe to exist.
        RecipeTemplate saved = repo.save(copy);

        RecipeProcessCopyResult processCopy;
        try {
            processCopy = processService.copyAllToRecipe(original.getId(), saved.getId());
        } catch (RuntimeException e) {
            // copyAllToRecipe validates every process before saving any, so nothing but the
            // template itself has been persisted at this point.
            repo.deleteById(saved.getId());
            throw e;
        }

        if (processCopy.mainProcessId() != null) {
            saved.setMainProcessId(processCopy.mainProcessId());
            saved = repo.save(saved);
        }


        return mapper.toDTO(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecipeDetailResponseDTO getRecipeDetails(Long recipeId, Long requestingUserId) {
        RecipeTemplate recipe = requireAccessible(recipeId, requestingUserId);
        return mapper.toDetailDTO(recipe);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecipeDetailResponseDTO updateIngredients(Long recipeId, Long userId, List<RecipeIngredientDTO> ingredients) {
        RecipeTemplate recipe = repo.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        requireOwner(recipe, userId);

        recipe.setIngredients(mapper.toIngredientEntities(ingredients));
        RecipeTemplate saved = repo.save(recipe);
        return mapper.toDetailDTO(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecipeDetailResponseDTO updateNutrition(Long recipeId, Long userId, NutritionInfoDTO nutrition) {
        RecipeTemplate recipe = repo.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        requireOwner(recipe, userId);

        recipe.setNutrition(mapper.toNutritionEntity(nutrition));
        RecipeTemplate saved = repo.save(recipe);
        return mapper.toDetailDTO(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessResponseDTO getMainProcess(Long recipeId, Long requestingUserId) {
        RecipeTemplate recipe = requireAccessible(recipeId, requestingUserId);
        if (recipe.getMainProcessId() == null) {
            throw new NoSuchElementException("Recipe " + recipeId + " does not have a main process yet");
        }
        return processService.get(recipeId, recipe.getMainProcessId(), requestingUserId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessResponseDTO createMainProcess(Long recipeId, Long userId) {
        RecipeTemplate recipe = repo.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        requireOwner(recipe, userId);

        if (recipe.getMainProcessId() != null) {
            // Idempotent: a main process already exists, so repeated calls never create a second one.
            return processService.get(recipeId, recipe.getMainProcessId(), userId);
        }

        // A MAIN process can exist without being linked yet (an earlier call that was interrupted,
        // or a concurrent one still in flight) — adopt it rather than creating a second one.
        Optional<ProcessResponseDTO> unlinkedMain = findMainProcess(recipeId, userId);
        if (unlinkedMain.isPresent()) {
            return processService.get(recipeId, linkMainProcess(recipeId, unlinkedMain.get().getId()), userId);
        }

        ProcessRequestDTO dto = new ProcessRequestDTO();
        dto.setType(ProcessType.MAIN);
        dto.setName(recipe.getName());
        dto.setDescription(recipe.getDescription());

        ProcessResponseDTO created;
        try {
            created = processService.create(recipeId, userId, dto);
        } catch (ProcessValidationException e) {
            // Lost a race: a concurrent call created the MAIN process between the check above and
            // this insert, so the validator rejected a second one. Use that one instead.
            Optional<ProcessResponseDTO> concurrentMain = findMainProcess(recipeId, userId);
            if (concurrentMain.isEmpty()) {
                throw e;
            }
            return processService.get(recipeId, linkMainProcess(recipeId, concurrentMain.get().getId()), userId);
        }

        Long linkedId = linkMainProcess(recipeId, created.getId());
        if (!created.getId().equals(linkedId)) {
            // Both concurrent calls inserted a MAIN process; the other one was linked first, so
            // this one is an unreferenced duplicate.
            processService.delete(recipeId, created.getId(), userId);
            return processService.get(recipeId, linkedId, userId);
        }
        return created;
    }

    private Optional<ProcessResponseDTO> findMainProcess(Long recipeId, Long userId) {
        return processService.listByRecipe(recipeId, userId)
                .stream()
                .filter(process -> process.getType() == ProcessType.MAIN)
                .findFirst();
    }

    /**
     * Links {@code processId} as the recipe's main process unless it already has one, and returns
     * whichever process id the recipe actually ends up linked to.
     */
    private Long linkMainProcess(Long recipeId, Long processId) {
        repo.linkMainProcessIfUnset(recipeId, processId);
        return repo.findById(recipeId)
                .map(RecipeTemplate::getMainProcessId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
    }

    /**
     * Verifies that a recipe is accessible for reading by the given user: the owner can always
     * read it; a non-owner (or unauthenticated caller) can read it only if it is
     * {@link Visibility#PUBLIC}.
     *
     * @param recipeId         the id of the recipe to fetch
     * @param requestingUserId the id of the requesting user, or null if unauthenticated
     * @return the recipe, if accessible
     * @throws NoSuchElementException      if no recipe exists with the given id
     * @throws RecipeAccessDeniedException if the recipe is private and requestingUserId is not its owner
     */
    private RecipeTemplate requireAccessible(Long recipeId, Long requestingUserId) {
        RecipeTemplate recipe = repo.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        if (recipe.getVisibility() == Visibility.PUBLIC) {
            return recipe;
        }
        if (requestingUserId != null && requestingUserId.equals(recipe.getCreatedBy())) {
            return recipe;
        }
        throw new RecipeAccessDeniedException("You do not have permission to view this recipe");
    }

    /**
     * Verifies that userId is the owner of the given recipe template.
     *
     * @param pt     the template to check ownership of
     * @param userId the id of the user attempting the operation
     * @throws RecipeAccessDeniedException if userId is null or does not match the template owner
     */
    private void requireOwner(RecipeTemplate pt, Long userId){
        if (userId == null || !userId.equals(pt.getCreatedBy())) {
            throw new RecipeAccessDeniedException("You do not have permission to modify this recipe");
        }
    }

    private List<RecipeIngredient> cloneIngredients(List<RecipeIngredient> ingredients) {
        List<RecipeIngredient> result = new ArrayList<>();
        if (ingredients == null) {
            return result;
        }

        for (RecipeIngredient ingredient : ingredients) {
            RecipeIngredient copy = new RecipeIngredient();
            copy.setIngredientId(ingredient.getIngredientId());
            copy.setQuantity(ingredient.getQuantity());
            copy.setUnit(ingredient.getUnit());
            copy.setNotes(ingredient.getNotes());
            copy.setPreparation(ingredient.getPreparation());
            result.add(copy);
        }

        return result;
    }

    private NutritionInfo cloneNutrition(NutritionInfo nutrition) {
        if (nutrition == null) {
            return null;
        }

        NutritionInfo copy = new NutritionInfo();
        copy.setCalories(nutrition.getCalories());
        copy.setProteinGrams(nutrition.getProteinGrams());
        copy.setCarbohydratesGrams(nutrition.getCarbohydratesGrams());
        copy.setFatGrams(nutrition.getFatGrams());
        copy.setFiberGrams(nutrition.getFiberGrams());
        copy.setSodiumMilligrams(nutrition.getSodiumMilligrams());
        copy.setServings(nutrition.getServings());
        return copy;
    }
}
