package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.StepDefinitionMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeStepDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link IStepDefinitionService} implementation. Persists step definitions via
 * {@link RecipeStepDefinitionRepository}, assigning each new definition a sequence-generated id
 * before saving, and maps between entities and DTOs via {@link StepDefinitionMapper}.
 */
@Service
public class RecipeStepDefinitionServiceImpl implements IStepDefinitionService {

    @Autowired
    private RecipeStepDefinitionRepository repo;

    @Autowired
    private StepDefinitionMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates and persists a new step definition, assigning it a new sequence-generated id.
     *
     * @param dto the step definition details to persist
     * @return the created step definition
     */
    @Override
    public RecipeStepDefinitionResponseDTO create(RecipeStepDefinitionRequestDTO dto){
        RecipeStepDefinition step = mapper.toEntity(dto);
        step.setId(seq.generateSequence(RecipeStepDefinition.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(step));
    }

    /**
     * Retrieves every step definition in the catalog.
     *
     * @return all known step definitions
     */
    @Override
    public List<RecipeStepDefinitionResponseDTO> getAll(){
        return repo.findAll()
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
