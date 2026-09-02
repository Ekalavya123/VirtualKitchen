package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.common.mapper.InventoryMapper;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryResponseDTO;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.kitchen.repository.InventoryRepository;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import com.processVisualisation.virtualKitchen.store.repository.EquipmentRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements IInventoryService {

    @Autowired
    private InventoryRepository repo;

    @Autowired
    private InventoryMapper mapper;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private EquipmentRepository equipmentRepository;

    @Autowired
    private SequenceGeneratorService seq;

    @Override
    public InventoryResponseDTO addOrUpdate(InventoryRequestDTO dto){

        Inventory inv = null;

        // Prefer kitchen-scoped inventory when kitchenId is provided
        if (dto.getKitchenId() != null) {
            inv = repo.findByKitchenIdAndItemTypeAndItemId(
                    dto.getKitchenId(), dto.getItemType(), dto.getItemId()
            ).orElse(null);
        }

        // Fallback to user-scoped inventory for backward compatibility
        if (inv == null) {
            inv = repo.findByUserIdAndItemTypeAndItemId(
                    dto.getUserId(), dto.getItemType(), dto.getItemId()
            ).orElse(null);
        }

        if(inv == null){
            inv = mapper.toEntity(dto);
            inv.setId(seq.generateSequence(Inventory.SEQUENCE_NAME));
        } else {
            inv.setQuantity(inv.getQuantity() + dto.getQuantity());
        }

        // If inventory existed but didn't have kitchenId set, and request provides it, persist it
        if (inv.getKitchenId() == null && dto.getKitchenId() != null) {
            inv.setKitchenId(dto.getKitchenId());
        }

        inv.setLastUpdated(LocalDateTime.now());

        return mapper.toDTO(repo.save(inv));
    }

    @Override
    public List<InventoryResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<InventoryResponseDTO> getByKitchen(Long kitchenId){
        return repo.findByKitchenId(kitchenId)
                .stream()
                .map(inv -> {
                    InventoryResponseDTO dto = mapper.toDTO(inv);

                    // Enrich with item name similar to KitchenInventoryService
                    if (inv.getItemType() == ItemType.INGREDIENT) {
                        ingredientRepository.findById(inv.getItemId()).ifPresent(ing -> dto.setItemName(ing.getName()));
                    } else if (inv.getItemType() == ItemType.EQUIPMENT) {
                        equipmentRepository.findById(inv.getItemId()).ifPresent(eq -> dto.setItemName(eq.getName()));
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }
}
