package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeStepExecutionRepository extends MongoRepository<RecipeStepExecution, Long> {

    List<RecipeStepExecution> findByProcessExecutionIdOrderByIdAsc(Long processExecutionId);
}
