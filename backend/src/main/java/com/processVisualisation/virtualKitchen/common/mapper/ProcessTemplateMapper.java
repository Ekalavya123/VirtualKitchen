package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeTemplate} entity and its
 * {@link RecipeTemplateRequestDTO}/{@link RecipeTemplateResponseDTO}
 * representations.
 */
@Component
public class ProcessTemplateMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeTemplate}
     * entity. Lossy/derived field: {@code visibility} is always hardcoded to
     * {@link Visibility#PRIVATE} regardless of the DTO's contents. The
     * entity's {@code id}, {@code createdAt} and {@code updatedAt} are left
     * unset, since they are assigned at persistence time.
     *
     * @param dto the request payload describing the recipe template to create
     * @return a new, unpersisted {@link RecipeTemplate} entity populated from {@code dto}
     */
    public RecipeTemplate toEntity(RecipeTemplateRequestDTO dto){
        RecipeTemplate pt = new RecipeTemplate();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        pt.setCreatedBy(dto.getCreatedBy());
        pt.setVisibility(Visibility.PRIVATE);
        return pt;
    }

    /**
     * Converts a {@link RecipeTemplate} entity into its response DTO
     * representation for returning to clients.
     *
     * @param pt the entity to convert
     * @return a fully populated {@link RecipeTemplateResponseDTO}
     */
    public RecipeTemplateResponseDTO toDTO(RecipeTemplate pt){
        return RecipeTemplateResponseDTO.builder()
                .id(pt.getId())
                .name(pt.getName())
                .description(pt.getDescription())
                .createdBy(pt.getCreatedBy())
                .visibility(pt.getVisibility())
                .createdAt(pt.getCreatedAt())
                .updatedAt(pt.getUpdatedAt())
                .build();
    }
}
