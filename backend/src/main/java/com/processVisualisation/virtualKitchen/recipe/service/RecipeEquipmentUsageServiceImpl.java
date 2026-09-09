package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.mapper.ProcessEquipmentUsageMapper;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeEquipmentUsageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link IProcessEquipmentUsageService} implementation. Persists equipment usage
 * records via {@link RecipeEquipmentUsageRepository}, assigning each new record a
 * sequence-generated id before saving, and maps between entities and DTOs via
 * {@link ProcessEquipmentUsageMapper}.
 */
@Service
public class RecipeEquipmentUsageServiceImpl implements IProcessEquipmentUsageService {

    @Autowired
    private RecipeEquipmentUsageRepository repo;

    @Autowired
    private ProcessEquipmentUsageMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates and persists a new equipment usage record, assigning it a new sequence-generated id.
     *
     * @param dto the equipment usage details to persist
     * @return the created equipment usage record
     */
    @Override
    public RecipeEquipmentUsageResponseDTO create(RecipeEquipmentUsageRequestDTO dto){
        RecipeEquipmentUsage p = mapper.toEntity(dto);
        p.setId(seq.generateSequence(RecipeEquipmentUsage.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(p));
    }

    /**
     * Retrieves all equipment usage records for a given process execution.
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the equipment usage records for that execution
     */
    @Override
    public List<RecipeEquipmentUsageResponseDTO> getByProcess(Long processExecutionId){
        return repo.findByProcessExecutionId(processExecutionId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }
}
