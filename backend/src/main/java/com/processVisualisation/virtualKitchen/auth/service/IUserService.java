package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserUpdateDTO;

/**
 * Service contract for general user account management (create, fetch, update,
 * delete), independent of the authentication-specific flows exposed by
 * IAuthService. Implemented by UserServiceImpl.
 */
public interface IUserService {

    /**
     * Creates a new user account.
     *
     * @param request the payload describing the account to create
     * @return the created user
     */
    UserResponseDTO createUser(UserRequestDTO request);

    /**
     * Fetches a user account by its numeric identifier.
     *
     * @param id the identifier of the user to fetch
     * @return the matching user
     */
    UserResponseDTO getUserById(Long id);

    /**
     * Fetches a user account by its email address.
     *
     * @param email the email address of the user to fetch
     * @return the matching user
     */
    UserResponseDTO getUserByEmail(String email);

    /**
     * Updates the profile fields of an existing user account.
     *
     * @param id the identifier of the user to update
     * @param request the fields to update
     * @return the updated user
     */
    UserResponseDTO updateUser(Long id, UserUpdateDTO request);

    /**
     * Deletes a user account by its numeric identifier.
     *
     * @param id the identifier of the user to delete
     */
    void deleteUser(Long id);
}
