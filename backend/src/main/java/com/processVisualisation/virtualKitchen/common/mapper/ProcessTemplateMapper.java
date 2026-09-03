package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateMapper {

    public ProcessTemplate toEntity(ProcessTemplateRequestDTO dto){
        ProcessTemplate pt = new ProcessTemplate();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        pt.setCreatedBy(dto.getCreatedBy());
        pt.setVisibility(Visibility.PRIVATE);
        return pt;
    }

    public ProcessTemplateResponseDTO toDTO(ProcessTemplate pt){
        return ProcessTemplateResponseDTO.builder()
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
