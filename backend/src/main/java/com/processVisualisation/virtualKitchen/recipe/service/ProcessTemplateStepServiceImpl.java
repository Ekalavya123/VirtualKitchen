package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateStepMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateStepRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessTemplateStepServiceImpl implements IProcessTemplateStepService {

    @Autowired
    private RecipeTemplateStepRepository repo;

    @Autowired
    private ProcessTemplateStepMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeTemplateStepResponseDTO create(RecipeTemplateStepRequestDTO dto){
        RecipeTemplateStep step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeTemplateStep.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<RecipeTemplateStepResponseDTO> getSteps(Long processTemplateId){
        return repo.findByProcessTemplateIdOrderByStepOrderAsc(processTemplateId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
