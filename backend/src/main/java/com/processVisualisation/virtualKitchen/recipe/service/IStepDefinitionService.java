package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;

import java.util.List;

public interface IStepDefinitionService {

    RecipeStepDefinitionResponseDTO create(RecipeStepDefinitionRequestDTO dto);

    List<RecipeStepDefinitionResponseDTO> getAll();
}
