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

    @Override
    public void sendEmailVerificationOtp(String email) {
        User user = getUserByEmailOrThrow(email);

        if (user.isEmailVerified()) {
            throw new AuthException("This email is already verified", HttpStatus.BAD_REQUEST);
        }

        otpService.generateAndSendOtp(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION);
    }

    @Override
    public AuthResponseDTO verifyEmailOtp(VerifyOtpRequestDTO request) {
        User user = getUserByEmailOrThrow(request.getEmail());

        otpService.verifyAndConsume(user.getEmail(), request.getOtp(), OtpPurpose.EMAIL_VERIFICATION);

        user.setEmailVerified(true);
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Override
    public void sendLoginOtp(String email) {
        getUserByEmailOrThrow(email);
        otpService.generateAndSendOtp(email.trim().toLowerCase(), OtpPurpose.LOGIN);
    }

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

    @Override
    public void sendPasswordResetOtp(String email) {
        getUserByEmailOrThrow(email);
        otpService.generateAndSendOtp(email.trim().toLowerCase(), OtpPurpose.PASSWORD_RESET);
    }

    @Override
    public void verifyPasswordResetOtp(VerifyOtpRequestDTO request) {
        User user = getUserByEmailOrThrow(request.getEmail());
        otpService.verifyOnly(user.getEmail(), request.getOtp(), OtpPurpose.PASSWORD_RESET);
    }

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

    @Override
    public UserResponseDTO getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("User not found", HttpStatus.UNAUTHORIZED));

        return userMapper.toDTO(user);
    }

    private User getUserByEmailOrThrow(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new AuthException("No account found with this email", HttpStatus.NOT_FOUND));
    }

    private void createKitchenForUser(User user) {
        KitchenRequestDTO kitchenRequest = new KitchenRequestDTO();
        kitchenRequest.setName(user.getName() + "'s Virtual Kitchen");
        kitchenRequest.setOwnerId(user.getId());
        kitchenService.create(kitchenRequest);
    }

    private AuthResponseDTO buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUserType());

        return AuthResponseDTO.builder()
                .token(token)
                .user(userMapper.toDTO(user))
                .build();
    }
}
