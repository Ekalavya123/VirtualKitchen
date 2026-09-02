package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateStepMapper;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplateStep;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessTemplateStepRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessTemplateStepServiceImpl implements IProcessTemplateStepService {

    @Autowired
    private ProcessTemplateStepRepository repo;

    @Autowired
    private ProcessTemplateStepMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public ProcessTemplateStepResponseDTO create(ProcessTemplateStepRequestDTO dto){
        ProcessTemplateStep step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(ProcessTemplateStep.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<ProcessTemplateStepResponseDTO> getSteps(Long processTemplateId){
        return repo.findByProcessTemplateIdOrderByStepOrderAsc(processTemplateId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
