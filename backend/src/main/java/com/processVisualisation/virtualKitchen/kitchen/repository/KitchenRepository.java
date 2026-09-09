package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for persisting and querying {@code Kitchen}
 * entities.
 */
public interface KitchenRepository extends MongoRepository<Kitchen, Long> {

    /**
     * Fetches all kitchens owned by the given user/owner id.
     */
    List<Kitchen> findByOwnerId(Long ownerId);
}
