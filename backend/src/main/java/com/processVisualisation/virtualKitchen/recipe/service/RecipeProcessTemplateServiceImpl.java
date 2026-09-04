package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplate;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeProcessTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeProcessTemplateServiceImpl implements RecipeProcessTemplateService {

    @Autowired
    private RecipeProcessTemplateRepository repo;

    @Autowired
    private ProcessTemplateMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public RecipeProcessTemplateResponseDTO create(RecipeProcessTemplateRequestDTO dto){
        RecipeProcessTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(RecipeProcessTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public RecipeProcessTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    @Override
    public List<RecipeProcessTemplateResponseDTO> getByUser(Long userId){
        List<RecipeProcessTemplateResponseDTO> templates = repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
        return templates;
    }

    @Override
    public RecipeProcessTemplateResponseDTO update(Long id, RecipeProcessTemplateUpdateDTO dto){
        RecipeProcessTemplate pt = repo.findById(id).orElseThrow();
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public void delete(Long id){
        repo.deleteById(id);
    }
}

