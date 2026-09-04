package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.StepExecutionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepStatus;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeStepExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeStepExecutionServiceImpl implements RecipeStepExecutionService {

    @Autowired
    private RecipeStepExecutionRepository repo;

    @Autowired
    private StepExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeStepExecutionResponseDTO create(RecipeStepExecutionRequestDTO dto){
        RecipeStepExecution step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeStepExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    @Override
    public RecipeStepExecutionResponseDTO update(Long id, RecipeStepExecutionUpdateDTO dto){
        RecipeStepExecution step = repo.findById(id).orElseThrow();

        RecipeStepStatus newStatus = RecipeStepStatus.valueOf(dto.getStatus());
        step.setStatus(newStatus);

        if(newStatus == RecipeStepStatus.IN_PROGRESS){
            step.setStartedAt(LocalDateTime.now());
        }

        if(newStatus == RecipeStepStatus.DONE){
            step.setCompletedAt(LocalDateTime.now());
        }

        step.setNotes(dto.getNotes());

        return mapper.toDTO(repo.save(step));
    }

    @Override
    public List<RecipeStepExecutionResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionIdOrderByIdAsc(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

