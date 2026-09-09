package com.processVisualisation.virtualKitchen.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outbound payload returned by every authentication endpoint that results in
 * an authenticated session (login, signup verification, Google auth, OTP
 * login), pairing the issued JWT access token with the authenticated user
 * profile.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponseDTO {
    private String token;
    private UserResponseDTO user;
}
