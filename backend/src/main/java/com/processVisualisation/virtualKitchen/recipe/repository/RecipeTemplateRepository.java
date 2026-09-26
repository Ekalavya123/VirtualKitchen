package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeTemplate} documents, which represent the
 * reusable, user-authored recipe/process templates (name, description, owner, visibility) that
 * template steps, executions, and copies are built from.
 */
public interface RecipeTemplateRepository extends MongoRepository<RecipeTemplate, Long> {

    /**
     * Fetches all recipe templates owned by a given user.
     *
     * @param createdBy the id of the owning user to filter by
     * @return the templates created by that user
     */
    List<RecipeTemplate> findByCreatedBy(Long createdBy);

    /**
     * Fetches all recipe templates with the given visibility that were NOT created by the given
     * user, used to list globally shared/public recipes available to a user.
     *
     * @param visibility the visibility to filter by (e.g. {@code PUBLIC})
     * @param createdBy  the id of the user to exclude as owner
     * @return the matching templates owned by other users
     */
    List<RecipeTemplate> findByVisibilityAndCreatedByNot(Visibility visibility, Long createdBy);

    /**
     * Atomically sets a recipe's {@code mainProcessId}, but only if it has none yet, so two
     * concurrent "ensure a MAIN process" requests can never both link their own MAIN.
     *
     * @return the number of recipes updated: 1 if this call linked the process, 0 if the recipe
     *         already had a main process (or doesn't exist)
     */
    @Query("{ '_id': ?0, 'mainProcessId': null }")
    @Update("{ '$set': { 'mainProcessId': ?1 } }")
    long linkMainProcessIfUnset(Long recipeId, Long processId);
}
