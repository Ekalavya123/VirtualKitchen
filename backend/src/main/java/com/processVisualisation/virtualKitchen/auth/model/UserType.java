package com.processVisualisation.virtualKitchen.auth.model;

/**
 * Role assigned to a User account, used to derive the granted authority
 * (ROLE_ADMIN / ROLE_USER) applied by JwtAuthenticationFilter for
 * authorization checks.
 */
public enum UserType {
    ADMIN,
    USER
}
