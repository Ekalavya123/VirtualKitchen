package com.processVisualisation.virtualKitchen.auth.service;

/**
 * Verified identity information extracted from a Google OAuth ID token,
 * returned by GoogleTokenVerifierService and used by AuthServiceImpl to
 * find or provision the corresponding local user account.
 *
 * @param googleId the stable Google account identifier (subject claim) for the user
 * @param email the email address associated with the Google account
 * @param name the display name associated with the Google account
 */
public record GoogleUserInfo(String googleId, String email, String name) {
}
