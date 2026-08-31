package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionResponseDTO;

import java.util.List;

public interface IStepDefinitionService {

    StepDefinitionResponseDTO create(StepDefinitionRequestDTO dto);

    List<StepDefinitionResponseDTO> getAll();
}
