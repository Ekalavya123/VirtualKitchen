package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;

import java.util.List;

public interface IProcessTemplateService {

    RecipeTemplateResponseDTO create(RecipeTemplateRequestDTO dto);

    RecipeTemplateResponseDTO get(Long id);

    List<RecipeTemplateResponseDTO> getByUser(Long userId);

    List<RecipeTemplateResponseDTO> getGlobalRecipes(Long userId);

    RecipeTemplateResponseDTO update(Long id, Long userId, RecipeTemplateUpdateDTO dto);

    void delete(Long id, Long userId);

    RecipeTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility);

    RecipeTemplateResponseDTO copyToUser(Long id, Long userId);
}

