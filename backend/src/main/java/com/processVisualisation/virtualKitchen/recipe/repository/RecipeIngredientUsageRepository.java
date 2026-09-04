package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredientUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeIngredientUsageRepository extends MongoRepository<RecipeIngredientUsage, Long> {

    List<RecipeIngredientUsage> findByProcessExecutionId(Long processExecutionId);
}
