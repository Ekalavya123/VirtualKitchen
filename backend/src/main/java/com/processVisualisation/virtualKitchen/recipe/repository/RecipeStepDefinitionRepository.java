package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RecipeStepDefinitionRepository extends MongoRepository<RecipeStepDefinition, Long> {
}
