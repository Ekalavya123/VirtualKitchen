package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.*;

/**
 * Service contract for the authentication lifecycle: signup, login, Google
 * OAuth sign-in, email verification, OTP-based login, password reset, and
 * lookup of the currently authenticated user. Implemented by AuthServiceImpl.
 */
public interface IAuthService {

    /**
     * Registers a new user account with email and password credentials and
     * triggers an email verification OTP.
     *
     * @param request the signup payload
     * @return the created user
     */
    UserResponseDTO signup(SignupRequestDTO request);

    /**
     * Authenticates a user with email and password credentials.
     *
     * @param request the login payload
     * @return the issued authentication token and user info
     */
    AuthResponseDTO login(LoginRequestDTO request);

    /**
     * Authenticates or provisions a user via a Google OAuth ID token.
     *
     * @param request the payload containing the Google ID token
     * @return the issued authentication token and user info
     */
    AuthResponseDTO googleAuth(GoogleAuthRequestDTO request);

    /**
     * Sends a one-time passcode to the given email address to verify account
     * ownership.
     *
     * @param email the destination email address
     */
    void sendEmailVerificationOtp(String email);

    /**
     * Verifies the email-verification OTP and activates the account.
     *
     * @param request the payload containing the email address and OTP code
     * @return the issued authentication token and user info
     */
    AuthResponseDTO verifyEmailOtp(VerifyOtpRequestDTO request);

    /**
     * Sends a one-time passcode to the given email address for signing in
     * without a password.
     *
     * @param email the destination email address
     */
    void sendLoginOtp(String email);

    /**
     * Verifies a login OTP and authenticates the user.
     *
     * @param request the payload containing the email address and OTP code
     * @return the issued authentication token and user info
     */
    AuthResponseDTO verifyLoginOtp(VerifyOtpRequestDTO request);

    /**
     * Sends a one-time passcode to the given email address to begin a password
     * reset flow.
     *
     * @param email the destination email address
     */
    void sendPasswordResetOtp(String email);

    /**
     * Verifies the password-reset OTP without consuming it, gating access to
     * the reset-password step.
     *
     * @param request the payload containing the email address and OTP code
     */
    void verifyPasswordResetOtp(VerifyOtpRequestDTO request);

    /**
     * Sets a new password for the account after a successful password-reset
     * OTP verification.
     *
     * @param request the payload containing the email address and new password
     */
    void resetPassword(ResetPasswordRequestDTO request);

    /**
     * Fetches the profile of the currently authenticated user.
     *
     * @param userId the identifier of the authenticated user
     * @return the matching user
     */
    UserResponseDTO getCurrentUser(Long userId);
}
