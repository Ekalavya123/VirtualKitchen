package com.processVisualisation.virtualKitchen.auth.model;

/**
 * Identifies how a User account authenticates: with locally stored
 * credentials, or via a linked Google OAuth identity.
 */
public enum AuthProvider {
    /** The account authenticates with a locally stored email/password credential. */
    LOCAL,
    /** The account authenticates via a linked Google OAuth identity. */
    GOOGLE
}
