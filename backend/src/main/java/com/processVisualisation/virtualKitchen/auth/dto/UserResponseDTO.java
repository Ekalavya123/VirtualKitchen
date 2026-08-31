package com.processVisualisation.virtualKitchen.auth.dto;

import com.processVisualisation.virtualKitchen.auth.model.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponseDTO {
    private Long id;
    private String name;
    private String email;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}