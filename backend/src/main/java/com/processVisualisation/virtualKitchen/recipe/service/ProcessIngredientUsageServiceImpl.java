package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessIngredientUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessIngredientUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessIngredientUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessIngredientUsageServiceImpl implements IProcessIngredientUsageService {

    @Autowired
    private ProcessIngredientUsageRepository repo;

    @Autowired
    private ProcessIngredientUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public ProcessIngredientUsageResponseDTO create(ProcessIngredientUsageRequestDTO dto){
        ProcessIngredientUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(ProcessIngredientUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    @Override
    public List<ProcessIngredientUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
