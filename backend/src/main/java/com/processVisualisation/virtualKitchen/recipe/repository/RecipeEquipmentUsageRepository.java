package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeEquipmentUsage} documents, which record which
 * pieces of equipment were used during a given recipe (process) execution.
 */
public interface RecipeEquipmentUsageRepository extends MongoRepository<RecipeEquipmentUsage, Long> {

    /**
     * Fetches all equipment usage records logged against a given process execution.
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the equipment usage records for that execution
     */
    List<RecipeEquipmentUsage> findByProcessExecutionId(Long processExecutionId);
}
