package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepResponseDTO;

import java.util.List;

public interface IProcessTemplateStepService {

    ProcessTemplateStepResponseDTO create(ProcessTemplateStepRequestDTO dto);

    List<ProcessTemplateStepResponseDTO> getSteps(Long processTemplateId);
}
