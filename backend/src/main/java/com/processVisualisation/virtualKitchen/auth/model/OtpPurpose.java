package com.processVisualisation.virtualKitchen.auth.model;

/**
 * Identifies why a one-time passcode (Otp) was issued, so the same OTP
 * mechanism can be reused across multiple flows without codes issued for one
 * purpose being valid for another.
 */
public enum OtpPurpose {
    /** Issued after signup to confirm ownership of the account email address. */
    EMAIL_VERIFICATION,
    /** Issued to authorize setting a new password during the forgot-password flow. */
    PASSWORD_RESET,
    /** Issued to authenticate a user signing in without a password. */
    LOGIN
}
