package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessIngredientUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessIngredientUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeProcessIngredientUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeProcessIngredientUsageServiceImpl implements RecipeProcessIngredientUsageService {

    @Autowired
    private RecipeProcessIngredientUsageRepository repo;

    @Autowired
    private ProcessIngredientUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeProcessIngredientUsageResponseDTO create(RecipeProcessIngredientUsageRequestDTO dto){
        RecipeProcessIngredientUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(RecipeProcessIngredientUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    @Override
    public List<RecipeProcessIngredientUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

