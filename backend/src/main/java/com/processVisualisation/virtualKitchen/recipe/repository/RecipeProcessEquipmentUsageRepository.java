package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessEquipmentUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeProcessEquipmentUsageRepository extends MongoRepository<RecipeProcessEquipmentUsage, Long> {

    List<RecipeProcessEquipmentUsage> findByProcessExecutionId(Long processExecutionId);
}
