package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessIngredientUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredientUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeIngredientUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeIngredientUsageServiceImpl implements IProcessIngredientUsageService {

    @Autowired
    private RecipeIngredientUsageRepository repo;

    @Autowired
    private ProcessIngredientUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeIngredientUsageResponseDTO create(RecipeIngredientUsageRequestDTO dto){
        RecipeIngredientUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(RecipeIngredientUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    @Override
    public List<RecipeIngredientUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
