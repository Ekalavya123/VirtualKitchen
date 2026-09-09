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

/**
 * Default implementation of {@link IInventoryService}.
 * <p>
 * Manages inventory items that may be scoped to a kitchen or to a user. When
 * adding or updating stock, kitchen-scoped matches are preferred over
 * user-scoped matches; if no existing record is found a new one is created
 * with an id generated via {@link SequenceGeneratorService}, otherwise the
 * existing record's quantity is incremented. Kitchen-scoped lookups are
 * enriched with the item's display name via the ingredient/equipment
 * repositories.
 */
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

    /**
     * Creates a new inventory record or increments the quantity of an
     * existing one. Kitchen-scoped inventory is looked up first when
     * {@code dto.getKitchenId()} is present; if no match is found (or no
     * kitchen id was supplied) the method falls back to a user-scoped lookup.
     * When no existing record matches, a new one is created with a generated
     * id; otherwise the existing record's quantity is incremented by the
     * requested amount. If an existing record has no kitchen id set but the
     * request supplies one, it is backfilled onto the record. The record's
     * {@code lastUpdated} timestamp is refreshed and the entity is persisted.
     *
     * @param dto the item being added/updated, including its user/kitchen
     *            scope, item type/id, quantity and unit
     * @return the resulting inventory record's current state
     */
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

    /**
     * Fetches all inventory items owned directly by the given user.
     *
     * @param userId id of the owning user
     * @return the user's inventory items
     */
    @Override
    public List<InventoryResponseDTO> getByUser(Long userId){
        return repo.findByUserId(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Fetches all inventory items allocated to the given kitchen, enriching
     * each result with the item's display name looked up from the
     * ingredient or equipment repository based on its item type.
     *
     * @param kitchenId id of the kitchen
     * @return the kitchen's inventory items, with item names populated
     */
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
