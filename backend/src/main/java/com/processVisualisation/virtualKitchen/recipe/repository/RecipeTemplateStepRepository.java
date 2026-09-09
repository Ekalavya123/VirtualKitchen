package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeTemplateStep} documents, which represent the
 * ordered steps that make up a recipe/process template.
 */
public interface RecipeTemplateStepRepository extends MongoRepository<RecipeTemplateStep, Long> {

    /**
     * Fetches all steps belonging to a given process template, ordered by their step order
     * ascending.
     *
     * @param processTemplateId the id of the owning process template
     * @return the template's steps, ordered by step order ascending
     */
    List<RecipeTemplateStep> findByProcessTemplateIdOrderByStepOrderAsc(Long processTemplateId);
}
