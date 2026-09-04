package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeProcessExecutionRepository extends MongoRepository<RecipeProcessExecution, Long> {

    List<RecipeProcessExecution> findByUserId(Long userId);

    List<RecipeProcessExecution> findByProcessTemplateId(Long processTemplateId);
}
