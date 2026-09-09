package com.processVisualisation.virtualKitchen.kitchen.repository;

import com.processVisualisation.virtualKitchen.kitchen.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for persisting and querying {@code Order}
 * entities.
 */
public interface OrderRepository extends MongoRepository<Order, Long> {

    /**
     * Fetches all orders placed by the given user, most recently created first.
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
