package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.StepDefinitionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeStepDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeStepDefinitionServiceImpl implements RecipeStepDefinitionService {

    @Autowired
    private RecipeStepDefinitionRepository repo;

    @Autowired
    private StepDefinitionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeStepDefinitionResponseDTO create(RecipeStepDefinitionRequestDTO dto){
        RecipeStepDefinition step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeStepDefinition.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<RecipeStepDefinitionResponseDTO> getAll(){
        return repo.findAll()
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

