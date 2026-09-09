package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessExecutionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStatus;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link IProcessExecutionService} implementation. Persists recipe (process) executions
 * via {@link RecipeExecutionRepository}, assigning each new execution a sequence-generated id,
 * and drives status transitions (stamping {@code completedAt} when a run reaches
 * {@link RecipeStatus#DONE}). Maps between entities and DTOs via {@link ProcessExecutionMapper}.
 */
@Service
public class RecipeExecutionServiceImpl implements IProcessExecutionService {

    @Autowired
    private RecipeExecutionRepository repo;

    @Autowired
    private ProcessExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates and persists a new recipe execution, assigning it a new sequence-generated id.
     *
     * @param dto the details needed to start the execution
     * @return the newly created execution
     */
    @Override
    public RecipeExecutionResponseDTO start(RecipeExecutionRequestDTO dto){
        RecipeExecution pe = mapper.toEntity(dto);
        pe.setId(seq.generateSequence(RecipeExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pe));
    }

    /**
     * Transitions an existing recipe execution to a new status, stamping {@code completedAt}
     * when the new status is {@link RecipeStatus#DONE}.
     *
     * @param id     the id of the execution to update
     * @param status the new status name, parsed via {@link RecipeStatus#valueOf(String)}
     * @return the updated execution
     * @throws java.util.NoSuchElementException if no execution exists with the given id
     * @throws IllegalArgumentException if {@code status} does not match a {@link RecipeStatus} constant
     */
    @Override
    public RecipeExecutionResponseDTO updateStatus(Long id, String status){
        RecipeExecution pe = repo.findById(id).orElseThrow();

        RecipeStatus newStatus = RecipeStatus.valueOf(status);

        pe.setStatus(newStatus);

        if(newStatus == RecipeStatus.DONE){
            pe.setCompletedAt(LocalDateTime.now());
        }

        return mapper.toDTO(repo.save(pe));
    }

    /**
     * Retrieves all recipe executions started by a given user.
     *
     * @param userId the id of the user to filter by
     * @return the executions started by that user
     */
    @Override
    public List<RecipeExecutionResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
