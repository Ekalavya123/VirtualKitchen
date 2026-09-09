package com.processVisualisation.virtualKitchen.auth.repository;

import com.processVisualisation.virtualKitchen.auth.model.Otp;
import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for persisting and querying one-time
 * passcode (OTP) documents in the "otps" collection.
 */
@Repository
public interface OtpRepository extends MongoRepository<Otp, String> {

    /** Fetches the most recently created OTP for the given email and purpose, if one exists. */
    Optional<Otp> findTopByEmailAndPurposeOrderByCreatedAtDesc(String email, OtpPurpose purpose);

    /** Counts how many OTPs have been created for the given email and purpose since the given timestamp, used for resend rate limiting. */
    long countByEmailAndPurposeAndCreatedAtAfter(String email, OtpPurpose purpose, LocalDateTime after);
}
