package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessEquipmentUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeEquipmentUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeEquipmentUsageServiceImpl implements IProcessEquipmentUsageService {

    @Autowired
    private RecipeEquipmentUsageRepository repo;

    @Autowired
    private ProcessEquipmentUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeEquipmentUsageResponseDTO create(RecipeEquipmentUsageRequestDTO dto){
        RecipeEquipmentUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(RecipeEquipmentUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    @Override
    public List<RecipeEquipmentUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
