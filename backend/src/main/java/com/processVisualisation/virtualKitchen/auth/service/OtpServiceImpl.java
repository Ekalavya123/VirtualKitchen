package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.model.Otp;
import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;
import com.processVisualisation.virtualKitchen.auth.repository.OtpRepository;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.otp.expiration-minutes}")
    private long expirationMinutes;

    @Value("${app.otp.max-attempts}")
    private int maxAttempts;

    @Value("${app.otp.resend-window-minutes}")
    private long resendWindowMinutes;

    @Value("${app.otp.max-per-window}")
    private int maxPerWindow;

    public OtpServiceImpl(OtpRepository otpRepository, EmailService emailService, PasswordEncoder passwordEncoder) {
        this.otpRepository = otpRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void generateAndSendOtp(String email, OtpPurpose purpose) {
        long recentCount = otpRepository.countByEmailAndPurposeAndCreatedAtAfter(
                email, purpose, LocalDateTime.now().minusMinutes(resendWindowMinutes));

        if (recentCount >= maxPerWindow) {
            throw new AuthException(
                    "Too many verification codes requested. Please try again later.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        Otp otp = new Otp();
        otp.setEmail(email);
        otp.setPurpose(purpose);
        otp.setCodeHash(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));

        otpRepository.save(otp);

        emailService.sendOtpEmail(email, code, purpose);
    }

    @Override
    public void verifyAndConsume(String email, String code, OtpPurpose purpose) {
        Otp otp = validateAndGetActiveOtp(email, code, purpose);
        otp.setVerified(true);
        otp.setConsumed(true);
        otpRepository.save(otp);
    }

    @Override
    public void verifyOnly(String email, String code, OtpPurpose purpose) {
        Otp otp = validateAndGetActiveOtp(email, code, purpose);
        otp.setVerified(true);
        otpRepository.save(otp);
    }

    @Override
    public void consumeVerified(String email, OtpPurpose purpose) {
        Otp otp = otpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new AuthException("Please verify your code first", HttpStatus.BAD_REQUEST));

        if (!otp.isVerified() || otp.isConsumed()) {
            throw new AuthException("Please verify your code first", HttpStatus.BAD_REQUEST);
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("Verification has expired. Please request a new code.", HttpStatus.BAD_REQUEST);
        }

        otp.setConsumed(true);
        otpRepository.save(otp);
    }

    private Otp validateAndGetActiveOtp(String email, String code, OtpPurpose purpose) {
        Optional<Otp> latest = otpRepository.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose);

        Otp otp = latest.orElseThrow(
                () -> new AuthException("No verification code found. Please request a new one.", HttpStatus.BAD_REQUEST));

        if (otp.isConsumed() || otp.isVerified()) {
            throw new AuthException("This code has already been used. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("This code has expired. Please request a new one.", HttpStatus.BAD_REQUEST);
        }

        if (otp.getAttempts() >= maxAttempts) {
            throw new AuthException("Too many incorrect attempts. Please request a new code.", HttpStatus.TOO_MANY_REQUESTS);
        }

        otp.setAttempts(otp.getAttempts() + 1);
        otpRepository.save(otp);

        if (!passwordEncoder.matches(code, otp.getCodeHash())) {
            throw new AuthException("Incorrect verification code.", HttpStatus.BAD_REQUEST);
        }

        return otp;
    }
}
