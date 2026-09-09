package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Inbound payload for creating a new user account via the general user
 * management endpoint (UserController), carrying the account name, email
 * address, and plaintext password to be hashed before storage.
 */
@Data
public class UserRequestDTO {

        @NotBlank(message = "Name is required")
        private String name;

        @Email
        @NotBlank(message = "Email is required")
        private String email;

        @Size(min = 8, message = "Password must be at least 8 characters")
        private String password;
}
