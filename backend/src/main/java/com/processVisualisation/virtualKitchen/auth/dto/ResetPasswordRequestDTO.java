package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Inbound payload for completing the password-reset flow, carrying the
 * target account email plus the new password and its confirmation. Submitted
 * after the corresponding OTP has already been verified.
 */
@Data
public class ResetPasswordRequestDTO {

    @Email
    @NotBlank(message = "Email is required")
    private String email;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String confirmNewPassword;
}
