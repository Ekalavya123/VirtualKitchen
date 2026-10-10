package com.processVisualisation.virtualKitchen.auth.controller;

import com.processVisualisation.virtualKitchen.auth.model.DeliveryAddress;
import com.processVisualisation.virtualKitchen.auth.model.User;
import com.processVisualisation.virtualKitchen.auth.repository.UserRepository;
import com.processVisualisation.virtualKitchen.common.security.CurrentUser;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/** The authenticated user's default delivery address, used to pre-fill recipe orders. */
@RestController
@RequestMapping("/api/v1/users/me/delivery-address")
public class DeliveryAddressController {

    private final UserRepository userRepository;

    public DeliveryAddressController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public ApiResponse<DeliveryAddress> get() {
        return build(currentUser().getDeliveryAddress(), "fetched");
    }

    @PutMapping
    public ApiResponse<DeliveryAddress> put(@RequestBody DeliveryAddress address) {
        User user = currentUser();
        user.setDeliveryAddress(address);
        user.setUpdatedAt(LocalDateTime.now());
        return build(userRepository.save(user).getDeliveryAddress(), "saved");
    }

    private User currentUser() {
        Long userId = CurrentUser.requireUserId();
        return userRepository.findById(userId).orElseThrow(() -> new NoSuchElementException("User not found"));
    }

    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
