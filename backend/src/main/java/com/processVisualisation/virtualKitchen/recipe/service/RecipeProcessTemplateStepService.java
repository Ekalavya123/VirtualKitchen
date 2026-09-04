package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepResponseDTO;

import java.util.List;

public interface RecipeProcessTemplateStepService {

    RecipeProcessTemplateStepResponseDTO create(RecipeProcessTemplateStepRequestDTO dto);

    List<RecipeProcessTemplateStepResponseDTO> getSteps(Long processTemplateId);
}

