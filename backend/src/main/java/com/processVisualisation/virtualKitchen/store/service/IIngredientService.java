package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.IngredientRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientResponseDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientUpdateDTO;

import java.util.List;

public interface IIngredientService {

    IngredientResponseDTO createIngredient(IngredientRequestDTO request);

    IngredientResponseDTO getIngredientById(Long id);

    List<IngredientResponseDTO> getAllIngredients();

    IngredientResponseDTO updateIngredient(Long id, IngredientUpdateDTO request);

    void deleteIngredient(Long id);
}