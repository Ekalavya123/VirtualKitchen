package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.*;
import com.processVisualisation.virtualKitchen.auth.model.AuthProvider;
import com.processVisualisation.virtualKitchen.auth.model.OtpPurpose;
import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.model.UserType;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.mapper.UserMapper;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.service.IKitchenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Default implementation of IAuthService. Coordinates the full authentication
 * lifecycle: email/password signup and login, Google OAuth sign-in
 * (delegating token verification to GoogleTokenVerifierService), email
 * verification, OTP-based login, and the forgot/verify/reset password flow
 * (delegating OTP generation and validation to OtpService). Issues JWT access
 * tokens via JwtService on every successful authentication, and provisions a
 * default kitchen for each newly created user via IKitchenService.
 */
@Service
public class AuthServiceImpl implements IAuthService {

    private final UserRepository userRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final GoogleTokenVerifierService googleTokenVerifierService;
    private final IKitchenService kitchenService;

    public AuthServiceImpl(
            UserRepository userRepository,
            SequenceGeneratorService sequenceGeneratorService,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OtpService otpService,
            GoogleTokenVerifierService googleTokenVerifierService,
            IKitchenService kitchenService
    ) {
        this.userRepository = userRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.googleTokenVerifierService = googleTokenVerifierService;
        this.kitchenService = kitchenService;
    }

    /**
     * Registers a new local (email/password) user account, provisions a
     * default kitchen for the user, and sends an email verification OTP. The
     * account starts unverified until the OTP is confirmed.
     *
     * @param request the signup payload containing name, email, password, and confirmation
     * @return the created user
     * @throws AuthException if the password and confirmation do not match, or an account already exists for the given email
     */
    @Override
    public UserResponseDTO signup(SignupRequestDTO request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new AuthException("Password and confirmation do not match", HttpStatus.BAD_REQUEST);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("An account with this email already exists", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmailVerified(false);
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setUserType(UserType.USER);
        user.setId(sequenceGeneratorService.generateSequence(User.SEQUENCE_NAME));

        User saved = userRepository.save(user);
        createKitchenForUser(saved);

        otpService.generateAndSendOtp(saved.getEmail(), OtpPurpose.EMAIL_VERIFICATION);

        return userMapper.toDTO(saved);
    }

    /**
     * Authenticates a user with email and password credentials and issues an
     * access token.
     *
     * @param request the login payload containing email and password
     * @return the issued authentication token and user info
     * @throws AuthException if no account matches the email/password, or the account email has not been verified
     */
    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new AuthException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isEmailVerified()) {
            throw new AuthException("Please verify your email before signing in", HttpStatus.FORBIDDEN);
        }

        return buildAuthResponse(user);
    }

    /**
     * Authenticates or provisions a user via a Google OAuth ID token. If no
     * account exists for the verified Google email, a new account is created
     * (already email-verified) and a default kitchen is provisioned for it.
     * If an existing local account matches the email but has not yet been
     * linked to Google, it is linked to this Google identity and marked
     * verified. Issues an access token on success.
     *
     * @param request the payload containing the Google ID token to verify
     * @return the issued authentication token and user info
     * @throws AuthException if the Google token is invalid, expired, not issued for this application, or its email is unverified
     */
    @Override
    public AuthResponseDTO googleAuth(GoogleAuthRequestDTO request) {
        GoogleUserInfo googleUser = googleTokenVerifierService.verify(request.getIdToken());

        User user = userRepository.findByEmail(googleUser.email().trim().toLowerCase()).orElse(null);

        if (user == null) {
            user = new User();
            user.setName(googleUser.name());
            user.setEmail(googleUser.email().trim().toLowerCase());
            user.setEmailVerified(true);
            user.setAuthProvider(AuthProvider.GOOGLE);
            user.setGoogleId(googleUser.googleId());
            user.setUserType(UserType.USER);
            user.setId(sequenceGeneratorService.generateSequence(User.SEQUENCE_NAME));

            user = userRepository.save(user);
            createKitchenForUser(user);
        } else if (user.getGoogleId() == null) {
            // Link the existing (email/password) account to this Google identity instead of duplicating it.
            user.setGoogleId(googleUser.googleId());
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    /**
     * Sends a one-time passcode to the given email address to verify account
     * ownership after signup.
     *
     * @param email the destination email address
     * @throws AuthException if no account exists for the given email, or the email is already verified
     */
    @Override
    public void sendEmailVerificationOtp(String email) {
        User user = getUserByEmailOrThrow(email);

        if (user.isEmailVerified()) {
            throw new AuthException("This email is already verified", HttpStatus.BAD_REQUEST);
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);
    }

    /**
     * Verifies the email-verification OTP for the given account, marks the
     * account as email-verified, and issues an access token.
     *
     * @param request the payload containing the email address and OTP code to verify
     * @return the issued authentication token and user info
     * @throws AuthException if no account exists for the given email, or the OTP is missing, expired, already used, or does not match
     */
    @Override
    public AuthResponseDTO verifyEmailOtp(VerifyOtpRequestDTO request) {
        User user = getUserByEmailOrThrow(request.getEmail());

        otpService.verifyAndConsume(user.getEmail(), request.getOtp(), OtpPurpose.EMAIL_VERIFICATION);

        user.setEmailVerified(true);
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    /**
     * Sends a one-time passcode to the given email address for signing in
     * without a password.
     *
     * @param email the destination email address
     * @throws AuthException if no account exists for the given email
     */
    @Override
    public void sendLoginOtp(String email) {
        getUserByEmailOrThrow(email);
        otpService.generateAndSendOtp(email.trim().toLowerCase(), OtpPurpose.LOGIN);
    }

    /**
     * Verifies a login OTP and, on success, authenticates the user (also
     * marking the account email-verified if it was not already) and issues an
     * access token.
     *
     * @param request the payload containing the email address and OTP code to verify
     * @return the issued authentication token and user info
     * @throws AuthException if no account exists for the given email, or the OTP is missing, expired, already used, or does not match
     */
    @Override
    public AuthResponseDTO verifyLoginOtp(VerifyOtpRequestDTO request) {
        User user = getUserByEmailOrThrow(request.getEmail());

        otpService.verifyAndConsume(user.getEmail(), request.getOtp(), OtpPurpose.LOGIN);

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    /**
     * Sends a one-time passcode to the given email address to begin a
     * password reset flow.
     *
     * @param email the destination email address
     * @throws AuthException if no account exists for the given email
     */
    @Override
    public void sendPasswordResetOtp(String email) {
        getUserByEmailOrThrow(email);
        otpService.generateAndSendOtp(email.trim().toLowerCase(), OtpPurpose.PASSWORD_RESET);
    }

    /**
     * Verifies the password-reset OTP without consuming it, confirming the
     * caller is allowed to proceed to set a new password.
     *
     * @param request the payload containing the email address and OTP code to verify
     * @throws AuthException if no account exists for the given email, or the OTP is missing, expired, already used, or does not match
     */
    @Override
    public void verifyPasswordResetOtp(VerifyOtpRequestDTO request) {
        User user = getUserByEmailOrThrow(request.getEmail());
        otpService.verifyOnly(user.getEmail(), request.getOtp(), OtpPurpose.PASSWORD_RESET);
    }

    /**
     * Sets a new password for the account, requiring that a password-reset
     * OTP for this email has already been verified via verifyPasswordResetOtp.
     *
     * @param request the payload containing the email address, new password, and confirmation
     * @throws AuthException if the new password and confirmation do not match, no account exists for the given email, or no verified password-reset OTP is pending (or it has expired)
     */
    @Override
    public void resetPassword(ResetPasswordRequestDTO request) {
        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            throw new AuthException("Password and confirmation do not match", HttpStatus.BAD_REQUEST);
        }

        User user = getUserByEmailOrThrow(request.getEmail());

        otpService.consumeVerified(user.getEmail(), OtpPurpose.PASSWORD_RESET);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Fetches the profile of the currently authenticated user.
     *
     * @param userId the identifier of the authenticated user
     * @return the matching user
     * @throws AuthException if no user exists with the given id
     */
    @Override
    public UserResponseDTO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.UNAUTHORIZED));

        return userMapper.toDTO(user);
    }

    /**
     * Looks up a user by email, normalizing case and surrounding whitespace.
     *
     * @param email the email address to look up
     * @return the matching user
     * @throws AuthException if no account exists for the given email
     */
    private User getUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new AuthException("No account found with this email", HttpStatus.NOT_FOUND));
    }

    /**
     * Provisions a default kitchen owned by the given user, invoked once
     * after a new account is created (via signup or first-time Google sign-in).
     *
     * @param user the newly created user to provision a kitchen for
     */
    private void createKitchenForUser(User user) {
        KitchenRequestDTO kitchenRequest = new KitchenRequestDTO();
        kitchenRequest.setName(user.getName() + "'s Virtual Kitchen");
        kitchenRequest.setOwnerId(user.getId());
        kitchenService.create(kitchenRequest);
    }

    /**
     * Builds the authentication response for a user by issuing a fresh JWT
     * access token and mapping the user entity to its response DTO.
     *
     * @param user the user to build an authentication response for
     * @return the issued authentication token paired with the user profile
     */
    private AuthResponseDTO buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUserType());

        return AuthResponseDTO.builder()
                .token(token)
                .user(userMapper.toDTO(user))
                .build();
    }
}
