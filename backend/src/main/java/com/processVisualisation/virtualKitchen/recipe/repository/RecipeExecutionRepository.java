package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeExecutionRepository extends MongoRepository<RecipeExecution, Long> {

    List<RecipeExecution> findByUserId(Long userId);

    List<RecipeExecution> findByProcessTemplateId(Long processTemplateId);
}
