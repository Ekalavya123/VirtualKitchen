package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessExecutionResponseDTO;

import java.util.List;

public interface IProcessExecutionService {

    ProcessExecutionResponseDTO start(ProcessExecutionRequestDTO dto);

    ProcessExecutionResponseDTO updateStatus(Long id, String status);

    List<ProcessExecutionResponseDTO> getByUser(Long userId);
}
