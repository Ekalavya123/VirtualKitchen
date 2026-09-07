package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;

/**
 * Abstraction over email delivery so the underlying provider (SMTP, third-party API, etc.)
 * can be swapped without touching business logic.
 */
public interface EmailService {

    void sendOtpEmail(String to, String otp, OtpPurpose purpose);
}
