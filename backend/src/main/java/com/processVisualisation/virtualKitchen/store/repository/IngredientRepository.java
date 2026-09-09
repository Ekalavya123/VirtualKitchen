package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link Ingredient} catalog documents,
 * keyed by the numeric sequence id assigned at creation time.
 */
@Repository
public interface IngredientRepository extends MongoRepository<Ingredient, Long> {

    /** Fetches the ingredient document with the given unique name, if any. */
    Optional<Ingredient> findByName(String name);

    /** Checks whether an ingredient document with the given name already exists. */
    boolean existsByName(String name);
}