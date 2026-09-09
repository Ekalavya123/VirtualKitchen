package com.processVisualisation.virtualKitchen.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Inbound payload for updating the editable profile fields of an existing
 * user account. Currently limited to the display name.
 */
@Data
public class UserUpdateDTO {

    @NotBlank(message = "Name is required")
    private String name;
}
