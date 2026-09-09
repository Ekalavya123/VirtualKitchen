package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import org.springframework.data.mongodb.repository.MongoRepository;

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
}
