package com.processVisualisation.virtualKitchen.auth.model;

/**
 * Lifecycle status of a User account, independent of email verification,
 * used to enable or disable an account without deleting it.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE
}
