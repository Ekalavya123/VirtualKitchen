package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.StepDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StepDefinitionRepository extends MongoRepository<StepDefinition, Long> {
}
