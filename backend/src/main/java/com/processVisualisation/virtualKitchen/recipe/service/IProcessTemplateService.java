package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateUpdateDTO;

import java.util.List;

public interface IProcessTemplateService {

    ProcessTemplateResponseDTO create(ProcessTemplateRequestDTO dto);

    ProcessTemplateResponseDTO get(Long id);

    List<ProcessTemplateResponseDTO> getByUser(Long userId);

    ProcessTemplateResponseDTO update(Long id, ProcessTemplateUpdateDTO dto);

    void delete(Long id);
}
