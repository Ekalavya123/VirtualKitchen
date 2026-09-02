package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessEquipmentUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcessEquipmentUsageRepository extends MongoRepository<ProcessEquipmentUsage, Long> {

    List<ProcessEquipmentUsage> findByProcessExecutionId(Long processExecutionId);
}
