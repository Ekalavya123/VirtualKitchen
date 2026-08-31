package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.StepDefinitionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.StepDefinition;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.StepDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class StepDefinitionServiceImpl implements IStepDefinitionService {

    @Autowired
    private StepDefinitionRepository repo;

    @Autowired
    private StepDefinitionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public StepDefinitionResponseDTO create(StepDefinitionRequestDTO dto){
        StepDefinition step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(StepDefinition.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<StepDefinitionResponseDTO> getAll(){
        return repo.findAll()
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
