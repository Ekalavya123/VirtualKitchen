package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.ItemCost;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ItemCostRepository extends MongoRepository<ItemCost, Long> {

    List<ItemCost> findByItemTypeAndItemIdOrderByEffectiveFromDesc(String itemType, Long itemId);
}
