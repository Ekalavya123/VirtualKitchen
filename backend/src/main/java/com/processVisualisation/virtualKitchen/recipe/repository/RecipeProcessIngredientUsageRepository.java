package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessIngredientUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeProcessIngredientUsageRepository extends MongoRepository<RecipeProcessIngredientUsage, Long> {

    List<RecipeProcessIngredientUsage> findByProcessExecutionId(Long processExecutionId);
}
