package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link RecipeStepDefinition} documents, which describe
 * the reusable catalog of step definitions (e.g. named cooking actions) that recipe template
 * steps and step executions reference.
 */
public interface RecipeStepDefinitionRepository extends MongoRepository<RecipeStepDefinition, Long> {
}
