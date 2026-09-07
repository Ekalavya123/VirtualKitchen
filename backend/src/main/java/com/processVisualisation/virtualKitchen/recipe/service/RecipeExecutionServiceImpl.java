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

@Service
public class RecipeExecutionServiceImpl implements IProcessExecutionService {

    @Autowired
    private RecipeExecutionRepository repo;

    @Autowired
    private ProcessExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeExecutionResponseDTO start(RecipeExecutionRequestDTO dto){
        RecipeExecution pe = mapper.toEntity(dto);
        pe.setId(seq.generateSequence(RecipeExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pe));
    }

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

    @Override
    public List<RecipeExecutionResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
