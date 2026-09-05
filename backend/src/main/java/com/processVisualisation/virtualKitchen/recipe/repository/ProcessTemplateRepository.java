package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProcessTemplateRepository extends MongoRepository<ProcessTemplate, Long> {

    List<ProcessTemplate> findByCreatedBy(Long createdBy);

    List<ProcessTemplate> findByVisibilityAndCreatedByNot(Visibility visibility, Long createdBy);
}
