package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RecipeTemplateRepository extends MongoRepository<RecipeTemplate, Long> {

    List<RecipeTemplate> findByCreatedBy(Long createdBy);

    List<RecipeTemplate> findByVisibilityAndCreatedByNot(Visibility visibility, Long createdBy);
}
