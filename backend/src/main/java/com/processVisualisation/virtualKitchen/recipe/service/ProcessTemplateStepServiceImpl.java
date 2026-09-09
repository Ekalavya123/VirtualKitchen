package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateStepMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateStepRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link IProcessTemplateStepService} implementation. Persists recipe template steps
 * via {@link RecipeTemplateStepRepository}, assigning each new step a sequence-generated id
 * before saving, and maps between entities and DTOs via {@link ProcessTemplateStepMapper}.
 */
@Service
public class ProcessTemplateStepServiceImpl implements IProcessTemplateStepService {

    @Autowired
    private RecipeTemplateStepRepository repo;

    @Autowired
    private ProcessTemplateStepMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates and persists a new recipe template step, assigning it a new sequence-generated id.
     *
     * @param dto the step details to persist
     * @return the created step
     */
    @Override
    public RecipeTemplateStepResponseDTO create(RecipeTemplateStepRequestDTO dto){
        RecipeTemplateStep step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeTemplateStep.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    /**
     * Retrieves all steps for a given recipe template, ordered by step order ascending.
     *
     * @param processTemplateId the id of the owning template
     * @return the template's steps, ordered by step order
     */
    @Override
    public List<RecipeTemplateStepResponseDTO> getSteps(Long processTemplateId){
        return repo.findByProcessTemplateIdOrderByStepOrderAsc(processTemplateId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
