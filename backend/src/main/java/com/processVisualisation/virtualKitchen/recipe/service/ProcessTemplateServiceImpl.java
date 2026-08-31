package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplate;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessTemplateServiceImpl implements IProcessTemplateService {

    @Autowired
    private ProcessTemplateRepository repo;

    @Autowired
    private ProcessTemplateMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public ProcessTemplateResponseDTO create(ProcessTemplateRequestDTO dto){
        ProcessTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(ProcessTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public ProcessTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    @Override
    public List<ProcessTemplateResponseDTO> getByUser(Long userId){
        return repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProcessTemplateResponseDTO update(Long id, ProcessTemplateUpdateDTO dto){
        ProcessTemplate pt = repo.findById(id).orElseThrow();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public void delete(Long id){
        repo.deleteById(id);
    }
}
