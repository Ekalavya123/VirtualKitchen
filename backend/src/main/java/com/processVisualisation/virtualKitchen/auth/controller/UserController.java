package com.processVisualisation.virtualKitchen.auth.controller;

import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserUpdateDTO;
import com.processVisualisation.virtualKitchen.auth.service.IUserService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST controller exposing the user-management resource at /api/v1/users.
 * Provides CRUD operations over user accounts (create, fetch by id or email,
 * update, delete) independent of the authentication flows handled by
 * AuthController. Delegates all persistence and validation logic to
 * IUserService and wraps every response in a common ApiResponse envelope.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private IUserService userService;

    /**
     * Creates a new user account from the given request payload.
     *
     * @param request the payload containing the new user account details
     * @return a 201 Created response wrapping the created user
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponseDTO>> createUser(
            @Valid @RequestBody UserRequestDTO request) {

        UserResponseDTO response = userService.createUser(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildResponse(true, "User created successfully", response));
    }

    /**
     * Fetches a user account by its numeric identifier.
     *
     * @param id the identifier of the user to fetch
     * @return a 200 OK response wrapping the matching user
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUser(@PathVariable Long id) {

        UserResponseDTO response = userService.getUserById(id);

        return ResponseEntity.ok(
                buildResponse(true, "User fetched successfully", response)
        );
    }

    /**
     * Fetches a user account by its email address.
     *
     * @param email the email address of the user to fetch
     * @return a 200 OK response wrapping the matching user
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> getUserByEmail(@PathVariable String email) {

        UserResponseDTO response = userService.getUserByEmail(email);

        return ResponseEntity.ok(
                buildResponse(true, "User fetched successfully", response)
        );
    }

    /**
     * Updates the profile fields of an existing user account.
     *
     * @param id the identifier of the user to update
     * @param request the payload containing the fields to update
     * @return a 200 OK response wrapping the updated user
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponseDTO>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO request) {

        UserResponseDTO response = userService.updateUser(id, request);

        return ResponseEntity.ok(
                buildResponse(true, "User updated successfully", response)
        );
    }

    /**
     * Deletes a user account by its numeric identifier.
     *
     * @param id the identifier of the user to delete
     * @return a 200 OK response acknowledging the deletion
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {

        userService.deleteUser(id);

        return ResponseEntity.ok(
                buildResponse(true, "User deleted successfully", null)
        );
    }

    // Common Response Builder
    /**
     * Builds the common ApiResponse envelope used by every endpoint in this
     * controller.
     *
     * @param success whether the operation succeeded
     * @param message a human-readable status message
     * @param data the response payload, or null for endpoints with no body
     * @param <T> the type of the response payload
     * @return the assembled response envelope, timestamped at build time
     */
    private <T> ApiResponse<T> buildResponse(boolean success, String message, T data) {
        return ApiResponse.<T>builder()
                .success(success)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
