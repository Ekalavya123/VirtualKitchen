package com.processVisualisation.virtualKitchen.repository;

import com.processVisualisation.virtualKitchen.model.Inventory;
import com.processVisualisation.virtualKitchen.model.ItemType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends MongoRepository<Inventory, Long> {

    List<Inventory> findByUserId(Long userId);

    List<Inventory> findByKitchenId(Long kitchenId);

    Optional<Inventory> findByUserIdAndItemTypeAndItemId(
            Long userId, ItemType itemType, Long itemId
    );

    Optional<Inventory> findByKitchenIdAndItemTypeAndItemId(
            Long kitchenId, ItemType itemType, Long itemId
    );
}
