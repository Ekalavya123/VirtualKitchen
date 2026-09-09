package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.kitchen.model.KitchenInventory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for persisting and querying
 * {@code KitchenInventory} entities (the kitchen-to-inventory-item association
 * records).
 */
public interface KitchenInventoryRepository extends MongoRepository<KitchenInventory, Long> {

    /**
     * Fetches all kitchen-inventory allocation records for the given kitchen id.
     */
    List<KitchenInventory> findByKitchenId(Long kitchenId);
}
