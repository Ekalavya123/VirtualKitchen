package com.processVisualisation.virtualKitchen.auth.repository;

import com.processVisualisation.virtualKitchen.auth.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for persisting and querying User account
 * documents in the "users" collection.
 */
@Repository
public interface UserRepository extends MongoRepository<User, Long> {

    /** Fetches the user document whose email matches the given address, if one exists. */
    Optional<User> findByEmail(String email);

    /** Checks whether a user document already exists for the given email address. */
    boolean existsByEmail(String email);
}
