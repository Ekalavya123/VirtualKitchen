package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface KitchenRepository extends MongoRepository<Kitchen, Long> {

    List<Kitchen> findByOwnerId(Long ownerId);
}
