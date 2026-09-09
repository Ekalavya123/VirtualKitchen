package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserUpdateDTO;
import com.processVisualisation.virtualKitchen.common.exception.UserNotFoundException;
import com.processVisualisation.virtualKitchen.common.mapper.UserMapper;
import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Default implementation of IUserService. Handles general CRUD operations on
 * user accounts, delegating id generation to SequenceGeneratorService,
 * entity/DTO conversion to UserMapper, persistence to UserRepository, and
 * password hashing to PasswordEncoder.
 */
@Service
public class UserServiceImpl implements IUserService {

    private final UserRepository userRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            SequenceGeneratorService sequenceGeneratorService,
            UserMapper userMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a new user account, hashing the supplied password before
     * persisting it.
     *
     * @param request the payload describing the account to create
     * @return the created user
     * @throws RuntimeException if a user with the given email already exists
     */
    @Override
    public UserResponseDTO createUser(UserRequestDTO request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = userMapper.toEntity(request);
        user.setId(sequenceGeneratorService.generateSequence(User.SEQUENCE_NAME));
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User saved = userRepository.save(user);
        return userMapper.toDTO(saved);
    }

    /**
     * Fetches a user account by its numeric identifier.
     *
     * @param id the identifier of the user to fetch
     * @return the matching user
     * @throws UserNotFoundException if no user exists with the given id
     */
    @Override
    public UserResponseDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        return userMapper.toDTO(user);
    }

    /**
     * Fetches a user account by its email address.
     *
     * @param email the email address of the user to fetch
     * @return the matching user
     * @throws UserNotFoundException if no user exists with the given email
     */
    @Override
    public UserResponseDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        return userMapper.toDTO(user);
    }

    /**
     * Updates the profile fields of an existing user account.
     *
     * @param id the identifier of the user to update
     * @param request the fields to update
     * @return the updated user
     * @throws UserNotFoundException if no user exists with the given id
     */
    @Override
    public UserResponseDTO updateUser(Long id, UserUpdateDTO request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        user.setName(request.getName());

        User updated = userRepository.save(user);
        return userMapper.toDTO(updated);
    }

    /**
     * Deletes a user account by its numeric identifier.
     *
     * @param id the identifier of the user to delete
     * @throws UserNotFoundException if no user exists with the given id
     */
    @Override
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        userRepository.delete(user);
    }
}
