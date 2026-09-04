package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplate;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateMapper {

    public RecipeProcessTemplate toEntity(RecipeProcessTemplateRequestDTO dto){
        RecipeProcessTemplate pt = new RecipeProcessTemplate();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        pt.setCreatedBy(dto.getCreatedBy());
        return pt;
    }

    public RecipeProcessTemplateResponseDTO toDTO(RecipeProcessTemplate pt){
        return RecipeProcessTemplateResponseDTO.builder()
                .id(pt.getId())
                .name(pt.getName())
                .description(pt.getDescription())
                .createdBy(pt.getCreatedBy())
                .createdAt(pt.getCreatedAt())
                .updatedAt(pt.getUpdatedAt())
                .build();
    }
}

