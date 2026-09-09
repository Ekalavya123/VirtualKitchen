package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for persisting and querying {@code Inventory}
 * entities, supporting lookups scoped either to an individual user or to a
 * kitchen.
 */
public interface InventoryRepository extends MongoRepository<Inventory, Long> {

    /**
     * Fetches all inventory items owned directly by the given user.
     */
    List<Inventory> findByUserId(Long userId);

    /**
     * Fetches all inventory items allocated to the given kitchen.
     */
    List<Inventory> findByKitchenId(Long kitchenId);

    /**
     * Fetches the inventory record for a specific item type/id combination owned
     * by the given user, if one exists.
     */
    Optional<Inventory> findByUserIdAndItemTypeAndItemId(
            Long userId, ItemType itemType, Long itemId
    );

    /**
     * Fetches the inventory record for a specific item type/id combination
     * allocated to the given kitchen, if one exists.
     */
    Optional<Inventory> findByKitchenIdAndItemTypeAndItemId(
            Long kitchenId, ItemType itemType, Long itemId
    );
}
