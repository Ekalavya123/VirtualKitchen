package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.kitchen.model.KitchenInventory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface KitchenInventoryRepository extends MongoRepository<KitchenInventory, Long> {

    List<KitchenInventory> findByKitchenId(Long kitchenId);
}
