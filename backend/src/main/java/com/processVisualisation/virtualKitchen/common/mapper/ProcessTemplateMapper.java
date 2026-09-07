package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateMapper {

    public RecipeTemplate toEntity(RecipeTemplateRequestDTO dto){
        RecipeTemplate pt = new RecipeTemplate();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        pt.setCreatedBy(dto.getCreatedBy());
        pt.setVisibility(Visibility.PRIVATE);
        return pt;
    }

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
