package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Inbound payload for the Google OAuth sign-in endpoint, carrying the
 * Google-issued ID token to be verified against Google's tokeninfo endpoint.
 */
@Data
public class GoogleAuthRequestDTO {

    @NotBlank(message = "Google ID token is required")
    private String idToken;
}
