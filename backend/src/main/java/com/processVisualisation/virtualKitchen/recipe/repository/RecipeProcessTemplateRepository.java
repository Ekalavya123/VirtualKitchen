package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeProcessTemplateRepository extends MongoRepository<RecipeProcessTemplate, Long> {

    List<RecipeProcessTemplate> findByCreatedBy(Long createdBy);
}
