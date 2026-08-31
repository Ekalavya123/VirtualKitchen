package com.processVisualisation.virtualKitchen.store.repository;

import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IngredientRepository extends MongoRepository<Ingredient, Long> {

    Optional<Ingredient> findByName(String name);

    boolean existsByName(String name);
}