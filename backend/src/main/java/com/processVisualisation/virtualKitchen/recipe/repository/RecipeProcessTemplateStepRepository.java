package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplateStep;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeProcessTemplateStepRepository extends MongoRepository<RecipeProcessTemplateStep, Long> {

    List<RecipeProcessTemplateStep> findByProcessTemplateIdOrderByStepOrderAsc(Long processTemplateId);
}
