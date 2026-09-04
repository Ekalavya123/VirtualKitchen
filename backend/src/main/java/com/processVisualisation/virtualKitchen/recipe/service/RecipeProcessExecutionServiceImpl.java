package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessExecutionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessStatus;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeProcessExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeProcessExecutionServiceImpl implements RecipeProcessExecutionService {

    @Autowired
    private RecipeProcessExecutionRepository repo;

    @Autowired
    private ProcessExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeProcessExecutionResponseDTO start(RecipeProcessExecutionRequestDTO dto){
        RecipeProcessExecution pe = mapper.toEntity(dto);
        pe.setId(seq.generateSequence(RecipeProcessExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pe));
    }

    @Override
    public RecipeProcessExecutionResponseDTO updateStatus(Long id, String status){
        RecipeProcessExecution pe = repo.findById(id).orElseThrow();

        RecipeProcessStatus newStatus = RecipeProcessStatus.valueOf(status);

        pe.setStatus(newStatus);

        if(newStatus == RecipeProcessStatus.DONE){
            pe.setCompletedAt(LocalDateTime.now());
        }

        return mapper.toDTO(repo.save(pe));
    }

    @Override
    public List<RecipeProcessExecutionResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}

