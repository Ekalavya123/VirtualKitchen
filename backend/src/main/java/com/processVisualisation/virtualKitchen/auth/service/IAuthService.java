package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.*;

public interface IAuthService {

    UserResponseDTO signup(SignupRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);

    AuthResponseDTO googleAuth(GoogleAuthRequestDTO request);

    void sendEmailVerificationOtp(String email);

    AuthResponseDTO verifyEmailOtp(VerifyOtpRequestDTO request);

    void sendLoginOtp(String email);

    AuthResponseDTO verifyLoginOtp(VerifyOtpRequestDTO request);

    void sendPasswordResetOtp(String email);

    void verifyPasswordResetOtp(VerifyOtpRequestDTO request);

    void resetPassword(ResetPasswordRequestDTO request);

    UserResponseDTO getCurrentUser(Long userId);
}
