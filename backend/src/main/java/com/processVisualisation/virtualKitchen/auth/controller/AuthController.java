package com.processVisualisation.virtualKitchen.auth.controller;

import com.processVisualisation.virtualKitchen.auth.dto.*;
import com.processVisualisation.virtualKitchen.auth.service.IAuthService;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponseDTO>> signup(@Valid @RequestBody SignupRequestDTO request) {
        UserResponseDTO response = authService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                buildResponse(true, "Account created. Please verify the code sent to your email.", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);

        return ResponseEntity.ok(buildResponse(true, "Logged in successfully", response));
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> google(@Valid @RequestBody GoogleAuthRequestDTO request) {
        AuthResponseDTO response = authService.googleAuth(request);

        return ResponseEntity.ok(buildResponse(true, "Logged in successfully", response));
    }

    @PostMapping("/email/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendEmailVerificationOtp(@Valid @RequestBody EmailRequestDTO request) {
        authService.sendEmailVerificationOtp(request.getEmail());

        return ResponseEntity.ok(buildResponse(true, "Verification code sent to your email.", null));
    }

    @PostMapping("/email/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> verifyEmailOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        AuthResponseDTO response = authService.verifyEmailOtp(request);

        return ResponseEntity.ok(buildResponse(true, "Email verified successfully", response));
    }

    @PostMapping("/login/otp/send")
    public ResponseEntity<ApiResponse<Void>> sendLoginOtp(@Valid @RequestBody EmailRequestDTO request) {
        authService.sendLoginOtp(request.getEmail());

        return ResponseEntity.ok(buildResponse(true, "Sign-in code sent to your email.", null));
    }

    @PostMapping("/login/otp/verify")
    public ResponseEntity<ApiResponse<AuthResponseDTO>> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        AuthResponseDTO response = authService.verifyLoginOtp(request);

        return ResponseEntity.ok(buildResponse(true, "Logged in successfully", response));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody EmailRequestDTO request) {
        authService.sendPasswordResetOtp(request.getEmail());

        return ResponseEntity.ok(buildResponse(true, "Password reset code sent to your email.", null));
    }

    @PostMapping("/password/verify-otp")
    public ResponseEntity<ApiResponse<Void>> verifyPasswordResetOtp(@Valid @RequestBody VerifyOtpRequestDTO request) {
        authService.verifyPasswordResetOtp(request);

        return ResponseEntity.ok(buildResponse(true, "Code verified. You can now set a new password.", null));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);

        return ResponseEntity.ok(buildResponse(true, "Password updated. Please sign in.", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> me() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;

        if (!(principal instanceof Long userId)) {
            throw new AuthException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }

        UserResponseDTO response = authService.getCurrentUser(userId);

        return ResponseEntity.ok(buildResponse(true, "User fetched successfully", response));
    }

    private <T> ApiResponse<T> buildResponse(boolean success, String message, T data) {
        return ApiResponse.<T>builder()
                .success(success)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
