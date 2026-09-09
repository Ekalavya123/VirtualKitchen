package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.auth.model.User;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link User} entity and its
 * {@link UserRequestDTO}/{@link UserResponseDTO} representations.
 */
@Component
public class UserMapper {

    /**
     * Converts an incoming request DTO into a new {@link User} entity. Note
     * that {@code passwordHash} is set directly from the DTO's raw
     * {@code password} field without hashing — encoding is expected to
     * happen in the calling service before persistence. {@code status},
     * {@code emailVerified}, {@code authProvider}, {@code userType} and the
     * timestamps are left unset, since they are defaulted elsewhere or at
     * persistence time.
     *
     * @param dto the request payload describing the user to create
     * @return a new, unpersisted {@link User} entity populated from {@code dto}
     */
    public User toEntity(UserRequestDTO dto) {
        User user = new User();
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(dto.getPassword()); // encode in service
        return user;
    }

    /**
     * Converts a {@link User} entity into its response DTO representation
     * for returning to clients. The {@code passwordHash} field is
     * deliberately excluded from the response.
     *
     * @param user the entity to convert
     * @return a fully populated {@link UserResponseDTO}, excluding the password hash
     */
    public UserResponseDTO toDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .status(user.getStatus())
                .emailVerified(user.isEmailVerified())
                .authProvider(user.getAuthProvider())
                .userType(user.getUserType())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}