package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;

import java.util.List;

public interface IProcessTemplateService {

    ProcessTemplateResponseDTO create(ProcessTemplateRequestDTO dto);

    ProcessTemplateResponseDTO get(Long id);

    List<ProcessTemplateResponseDTO> getByUser(Long userId);

    List<ProcessTemplateResponseDTO> getGlobalRecipes(Long userId);

    ProcessTemplateResponseDTO update(Long id, Long userId, ProcessTemplateUpdateDTO dto);

    void delete(Long id, Long userId);

    ProcessTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility);

    ProcessTemplateResponseDTO copyToUser(Long id, Long userId);
}

