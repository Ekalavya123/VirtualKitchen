package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationClip;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AIVisualizationClipRepository extends MongoRepository<VisualizationClip, Long> {
    List<VisualizationClip> findByRecipeProcessIngredientUsageServiceImplIdOrderByStepOrderAsc(Long RecipeProcessIngredientUsageServiceImplId);
    Optional<VisualizationClip> findByRecipeProcessIngredientUsageServiceImplIdAndStepOrder(Long RecipeProcessIngredientUsageServiceImplId, int stepOrder);
}
