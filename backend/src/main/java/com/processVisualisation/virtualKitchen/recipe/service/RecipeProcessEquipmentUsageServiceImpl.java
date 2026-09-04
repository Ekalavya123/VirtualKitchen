package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessEquipmentUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessEquipmentUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeProcessEquipmentUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeProcessEquipmentUsageServiceImpl implements RecipeProcessEquipmentUsageService {

    @Autowired
    private RecipeProcessEquipmentUsageRepository repo;

    @Autowired
    private ProcessEquipmentUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeProcessEquipmentUsageResponseDTO create(RecipeProcessEquipmentUsageRequestDTO dto){
        RecipeProcessEquipmentUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(RecipeProcessEquipmentUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    @Override
    public List<RecipeProcessEquipmentUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

