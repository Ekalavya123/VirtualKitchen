package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateUpdateDTO;

import java.util.List;

public interface RecipeProcessTemplateService {

    RecipeProcessTemplateResponseDTO create(RecipeProcessTemplateRequestDTO dto);

    RecipeProcessTemplateResponseDTO get(Long id);

    List<RecipeProcessTemplateResponseDTO> getByUser(Long userId);

    RecipeProcessTemplateResponseDTO update(Long id, RecipeProcessTemplateUpdateDTO dto);

    void delete(Long id);
}

