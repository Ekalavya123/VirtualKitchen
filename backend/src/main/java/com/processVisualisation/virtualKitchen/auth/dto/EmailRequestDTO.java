package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Inbound payload carrying a single email address, used by the endpoints
 * that trigger sending an OTP (email verification, login OTP, and
 * forgot-password requests).
 */
@Data
public class EmailRequestDTO {

    @Email
    @NotBlank(message = "Email is required")
    private String email;
}
