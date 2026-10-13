package com.processVisualisation.virtualKitchen.auth.event;

/**
 * Published by {@code AuthService} once a new account has been created (email signup or first-time Google
 * sign-in). Other features listen for it to set the user up without auth depending on them.
 *
 * @param userId the new user's id
 * @param name   the new user's display name
 */
public record UserRegisteredEvent(Long userId, String name) {
}
