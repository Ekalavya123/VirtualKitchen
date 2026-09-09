package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;

/**
 * Abstraction over email delivery so the underlying provider (SMTP, third-party API, etc.)
 * can be swapped without touching business logic.
 */
public interface EmailService {

    /**
     * Sends an email containing the given OTP code to the specified address,
     * with subject and body tailored to the OTP purpose.
     *
     * @param to the destination email address
     * @param otp the one-time passcode to include in the email body
     * @param purpose the reason the OTP was generated (email verification, login, or password reset), used to select subject/body wording
     */
    void sendOtpEmail(String to, String otp, OtpPurpose purpose);
}
