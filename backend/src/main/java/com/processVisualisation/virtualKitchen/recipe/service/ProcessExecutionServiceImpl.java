package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessExecutionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessExecution;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessStatus;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessExecutionServiceImpl implements IProcessExecutionService {

    @Autowired
    private ProcessExecutionRepository repo;

    @Autowired
    private ProcessExecutionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public ProcessExecutionResponseDTO start(ProcessExecutionRequestDTO dto){
        ProcessExecution pe = mapper.toEntity(dto);
        pe.setId(seq.generateSequence(ProcessExecution.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pe));
    }

    @Override
    public ProcessExecutionResponseDTO updateStatus(Long id, String status){
        ProcessExecution pe = repo.findById(id).orElseThrow();

        ProcessStatus newStatus = ProcessStatus.valueOf(status);

        pe.setStatus(newStatus);

        if(newStatus == ProcessStatus.DONE){
            pe.setCompletedAt(LocalDateTime.now());
        }

        return mapper.toDTO(repo.save(pe));
    }

    @Override
    public List<ProcessExecutionResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
