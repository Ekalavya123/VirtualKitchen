package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.dto.IngredientRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientResponseDTO;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link Ingredient} entity and its
 * {@link IngredientRequestDTO}/{@link IngredientResponseDTO} representations.
 */
@Component
public class IngredientMapper {

    /**
     * Converts an incoming request DTO into a new {@link Ingredient} entity.
     * The entity's {@code id}, {@code createdAt} and {@code updatedAt} are
     * left unset, since they are assigned at persistence time.
     *
     * @param dto the request payload describing the ingredient to create
     * @return a new, unpersisted {@link Ingredient} entity populated from {@code dto}
     */
    public Ingredient toEntity(IngredientRequestDTO dto) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(dto.getName());
        ingredient.setDescription(dto.getDescription());
        ingredient.setDefaultUnit(dto.getDefaultUnit());
        return ingredient;
    }

    /**
     * Converts an {@link Ingredient} entity into its response DTO
     * representation for returning to clients.
     *
     * @param ingredient the entity to convert
     * @return a fully populated {@link IngredientResponseDTO}
     */
    public IngredientResponseDTO toDTO(Ingredient ingredient) {
        return IngredientResponseDTO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .description(ingredient.getDescription())
                .defaultUnit(ingredient.getDefaultUnit())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}