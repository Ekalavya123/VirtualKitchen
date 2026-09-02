package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.common.mapper.UserMapper;
import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.auth.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    @Test
    void shouldEncodePasswordBeforeSavingUser() {
        UserRepository userRepository = mock(UserRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserMapper userMapper = new UserMapper();

        when(userRepository.existsByEmail("chef@example.com")).thenReturn(false);
        when(sequenceGeneratorService.generateSequence(User.SEQUENCE_NAME)).thenReturn(42L);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                sequenceGeneratorService,
                userMapper,
                passwordEncoder
        );

        UserRequestDTO request = new UserRequestDTO();
        request.setName("Chef");
        request.setEmail("chef@example.com");
        request.setPassword("plaintext-password");

        UserResponseDTO response = service.createUser(request);

        verify(passwordEncoder).encode("plaintext-password");
        verify(userRepository).save(any(User.class));
        assertNotNull(response);
        assertEquals(42L, response.getId());
        assertEquals("chef@example.com", response.getEmail());
    }

    @Test
    void shouldPersistEncodedHashInsteadOfRawPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        SequenceGeneratorService sequenceGeneratorService = mock(SequenceGeneratorService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserMapper userMapper = new UserMapper();

        when(userRepository.existsByEmail("chef@example.com")).thenReturn(false);
        when(sequenceGeneratorService.generateSequence(User.SEQUENCE_NAME)).thenReturn(7L);
        when(passwordEncoder.encode("plaintext-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                sequenceGeneratorService,
                userMapper,
                passwordEncoder
        );

        UserRequestDTO request = new UserRequestDTO();
        request.setName("Chef");
        request.setEmail("chef@example.com");
        request.setPassword("plaintext-password");

        service.createUser(request);

        verify(userRepository).save(any(User.class));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            assertEquals("encoded-password", savedUser.getPasswordHash());
            assertNotEquals("plaintext-password", savedUser.getPasswordHash());
            return savedUser;
        });
    }
}