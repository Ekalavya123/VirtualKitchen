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

/**
 * Default {@link IStepExecutionService} implementation. Persists step executions via
 * {@link RecipeStepExecutionRepository}, assigning each new step execution a sequence-generated
 * id, and drives status transitions - stamping {@code startedAt} when a step moves to
 * {@link RecipeStepStatus#IN_PROGRESS} and {@code completedAt} when it reaches
 * {@link RecipeStepStatus#DONE}. Maps between entities and DTOs via {@link StepExecutionMapper}.
 */
@Service
public class RecipeStepExecutionServiceImpl implements IStepExecutionService {

    @Autowired
    private RecipeStepExecutionRepository repo;

    @Autowired
    private StepExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates and persists a new step execution record, assigning it a new sequence-generated id.
     *
     * @param dto the step execution details to persist
     * @return the created step execution
     */
    @Override
    public RecipeStepExecutionResponseDTO create(RecipeStepExecutionRequestDTO dto){
        RecipeStepExecution step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeStepExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    /**
     * Updates the status and notes of an existing step execution, stamping {@code startedAt}
     * when the new status is {@link RecipeStepStatus#IN_PROGRESS} and {@code completedAt} when
     * it is {@link RecipeStepStatus#DONE}.
     *
     * @param id  the id of the step execution to update
     * @param dto the new status and notes to apply
     * @return the updated step execution
     * @throws java.util.NoSuchElementException if no step execution exists with the given id
     * @throws IllegalArgumentException if {@code dto.getStatus()} does not match a {@link RecipeStepStatus} constant
     */
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

    /**
     * Retrieves all step executions belonging to a given process execution, ordered by id
     * ascending (i.e. creation order).
     *
     * @param processExecutionId the id of the owning process execution
     * @return the step executions for that process execution
     */
    @Override
    public List<RecipeStepExecutionResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionIdOrderByIdAsc(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
