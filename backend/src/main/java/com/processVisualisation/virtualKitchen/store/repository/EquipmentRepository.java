package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.Equipment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EquipmentRepository extends MongoRepository<Equipment, Long> {
    Optional<Equipment> findByName(String name);
    boolean existsByName(String name);
}
