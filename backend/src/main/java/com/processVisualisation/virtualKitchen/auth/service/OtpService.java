package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;

/**
 * Service contract governing the full lifecycle of one-time passcodes (OTPs)
 * used for email verification, OTP-based login, and password reset. Callers
 * generate/send a code, then either validate-and-consume it in one step or
 * validate it and consume it later (used to gate the password reset flow
 * behind a separately verified code). Implemented by OtpServiceImpl, which
 * also enforces expiration, attempt limits, and resend rate limiting.
 */
public interface OtpService {

    /** Generates a new OTP, persists its hash, and emails it to the user. Rate-limited per email+purpose. */
    void generateAndSendOtp(String email, OtpPurpose purpose);

    /** Validates the code and immediately marks the OTP as verified + consumed (single-step flows). */
    void verifyAndConsume(String email, String code, OtpPurpose purpose);

    /** Validates the code and marks it verified without consuming it (used to gate password reset). */
    void verifyOnly(String email, String code, OtpPurpose purpose);

    /** Confirms a previously-verified, not-yet-consumed OTP exists for this email+purpose, then consumes it. */
    void consumeVerified(String email, OtpPurpose purpose);
}
