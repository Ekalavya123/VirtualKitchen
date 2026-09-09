package com.processVisualisation.virtualKitchen.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Persistent entity representing a Virtual Kitchen user account, stored in
 * the "users" MongoDB collection. Captures identity (email, optional Google
 * account link), credentials (a hashed password for local accounts),
 * verification and lifecycle status, role, and audit timestamps. Populated
 * and read by UserRepository, and mapped to/from request and response DTOs
 * by UserMapper.
 */
@Data
@Document(collection = "users")
public class User {

    public static final String SEQUENCE_NAME = "users_sequence";

    @Id
    private Long id;

    private String name;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;

    private boolean emailVerified = false;

    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Indexed(unique = true, sparse = true)
    private String googleId;

    private UserType userType = UserType.USER;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private UserStatus status = UserStatus.ACTIVE;
}
