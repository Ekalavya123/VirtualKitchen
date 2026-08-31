package com.processVisualisation.virtualKitchen.auth.service;

import com.processVisualisation.virtualKitchen.auth.dto.UserRequestDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserResponseDTO;
import com.processVisualisation.virtualKitchen.auth.dto.UserUpdateDTO;

public interface IUserService {

    UserResponseDTO createUser(UserRequestDTO request);

    UserResponseDTO getUserById(Long id);

    UserResponseDTO getUserByEmail(String email);

    UserResponseDTO updateUser(Long id, UserUpdateDTO request);

    void deleteUser(Long id);
}