package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.ItemCost;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link ItemCost} documents, which
 * record the price-per-unit history of an ingredient or equipment item,
 * keyed by the numeric sequence id assigned at creation time.
 */
public interface ItemCostRepository extends MongoRepository<ItemCost, Long> {

    /**
     * Fetches all cost records for a given item, most recently effective
     * first, so the current price can be read from the head of the list.
     */
    List<ItemCost> findByItemTypeAndItemIdOrderByEffectiveFromDesc(String itemType, Long itemId);
}
