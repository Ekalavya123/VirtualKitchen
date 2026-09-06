package com.processVisualisation.virtualKitchen.auth.repository;

import com.processVisualisation.virtualKitchen.auth.model.Otp;
import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRepository extends MongoRepository<Otp, String> {

    Optional<Otp> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    long countByEmailAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, LocalDateTime after);
}
