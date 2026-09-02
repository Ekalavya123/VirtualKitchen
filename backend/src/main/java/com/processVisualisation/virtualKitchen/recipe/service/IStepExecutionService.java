package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.StepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.StepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.StepExecutionUpdateDTO;

import java.util.List;

public interface IStepExecutionService {

    StepExecutionResponseDTO create(StepExecutionRequestDTO dto);

    StepExecutionResponseDTO update(Long id, StepExecutionUpdateDTO dto);

    List<StepExecutionResponseDTO> getByProcess(Long processExecutionId);
}
