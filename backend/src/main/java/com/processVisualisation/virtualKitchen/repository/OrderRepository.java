package com.processVisualisation.virtualKitchen.repository;

import com.processVisualisation.virtualKitchen.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
