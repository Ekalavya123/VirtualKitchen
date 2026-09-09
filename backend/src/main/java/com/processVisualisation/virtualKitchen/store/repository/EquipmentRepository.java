package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.Equipment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link Equipment} catalog documents,
 * keyed by the numeric sequence id assigned at creation time.
 */
public interface EquipmentRepository extends MongoRepository<Equipment, Long> {

    /** Fetches the equipment document with the given unique name, if any. */
    Optional<Equipment> findByName(String name);

    /** Checks whether an equipment document with the given name already exists. */
    boolean existsByName(String name);
}
