package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendOtpEmail(String to, String otp, OtpPurpose purpose) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subjectFor(purpose));
        message.setText(bodyFor(otp, purpose));

        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Failed to send {} OTP email to {}", purpose, to, e);
            throw new AuthException("Unable to send verification email. Please try again later.", HttpStatus.BAD_GATEWAY);
        }
    }

    private String subjectFor(OtpPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> "Verify your Virtual Kitchen email";
            case PASSWORD_RESET -> "Reset your Virtual Kitchen password";
            case LOGIN -> "Your Virtual Kitchen sign-in code";
        };
    }

    private String bodyFor(String otp, OtpPurpose purpose) {
        String action = switch (purpose) {
            case EMAIL_VERIFICATION -> "verify your email";
            case PASSWORD_RESET -> "reset your password";
            case LOGIN -> "sign in";
        };

        return "Use the code below to " + action + " on Virtual Kitchen:\n\n"
                + otp
                + "\n\nThis code expires shortly and can only be used once. "
                + "If you did not request this, you can safely ignore this email.";
    }
}
