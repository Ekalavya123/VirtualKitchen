package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeTemplateStepRepository extends MongoRepository<RecipeTemplateStep, Long> {

    List<RecipeTemplateStep> findByProcessTemplateIdOrderByStepOrderAsc(Long processTemplateId);
}
