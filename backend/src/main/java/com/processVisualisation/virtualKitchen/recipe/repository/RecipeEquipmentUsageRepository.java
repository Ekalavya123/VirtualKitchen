package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeEquipmentUsageRepository extends MongoRepository<RecipeEquipmentUsage, Long> {

    List<RecipeEquipmentUsage> findByProcessExecutionId(Long processExecutionId);
}
