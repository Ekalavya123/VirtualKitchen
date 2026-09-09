package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Inbound payload for verifying a one-time passcode, carrying the target
 * email address and the OTP code submitted by the user. Reused across the
 * email-verification, login-OTP, and password-reset-OTP endpoints.
 */
@Data
public class VerifyOtpRequestDTO {

    @Email
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "OTP is required")
    private String otp;
}
