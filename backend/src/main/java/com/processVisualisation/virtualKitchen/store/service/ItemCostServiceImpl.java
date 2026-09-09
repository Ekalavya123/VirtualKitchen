package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.common.mapper.ItemCostMapper;
import com.processVisualisation.virtualKitchen.store.model.ItemCost;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;
import com.processVisualisation.virtualKitchen.store.repository.ItemCostRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Default {@link IItemCostService} implementation backed by MongoDB via
 * {@link ItemCostRepository}. Assigns identifiers using
 * {@link SequenceGeneratorService} and converts between entities and DTOs
 * using {@link ItemCostMapper}.
 */
@Service
public class ItemCostServiceImpl implements IItemCostService {

    @Autowired
    private ItemCostRepository repo;

    @Autowired
    private ItemCostMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    /**
     * Creates a new cost record for an ingredient or equipment item,
     * assigning it a generated sequence id.
     *
     * @param dto the item type, item id, unit, cost per unit, currency and effective date
     * @return the created cost record
     */
    @Override
    public ItemCostResponseDTO create(ItemCostRequestDTO dto){
        ItemCost cost = mapper.toEntity(dto);
        cost.setId(seq.generateSequence(ItemCost.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(cost));
    }
}
