package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateStepMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplateStep;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeProcessTemplateStepRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeProcessTemplateStepServiceImpl implements RecipeProcessTemplateStepService {

    @Autowired
    private RecipeProcessTemplateStepRepository repo;

    @Autowired
    private ProcessTemplateStepMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeProcessTemplateStepResponseDTO create(RecipeProcessTemplateStepRequestDTO dto){
        RecipeProcessTemplateStep step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeProcessTemplateStep.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<RecipeProcessTemplateStepResponseDTO> getSteps(Long processTemplateId){
        return repo.findByProcessTemplateIdOrderByStepOrderAsc(processTemplateId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

