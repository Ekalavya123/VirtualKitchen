package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;

import java.util.List;

public interface IProcessTemplateStepService {

    RecipeTemplateStepResponseDTO create(RecipeTemplateStepRequestDTO dto);

    List<RecipeTemplateStepResponseDTO> getSteps(Long processTemplateId);
}
